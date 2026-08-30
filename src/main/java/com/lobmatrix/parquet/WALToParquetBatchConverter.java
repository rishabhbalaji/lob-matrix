package com.lobmatrix.parquet;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.math.TradeStrengthClassifier;
import com.lobmatrix.engine.resample.MultiGridClockDispatcher;
import com.lobmatrix.engine.resample.ResampledGridPoint;
import com.lobmatrix.engine.target.CostModelRepository;
import com.lobmatrix.engine.target.ForwardReturnTargetEngine;
import com.lobmatrix.engine.target.ForwardReturnTargets;
import com.lobmatrix.wal.BinaryWALFrame;
import com.lobmatrix.wal.BinaryWALSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Automated Post-Market Batch Processor.
 * Converts raw WAL (.raw) files into structured columnar partitioned datasets for quantitative research.
 */
public class WALToParquetBatchConverter {

    private static final Logger log = LoggerFactory.getLogger(WALToParquetBatchConverter.class);

    private final Path baseOutputDir;

    public WALToParquetBatchConverter(Path baseOutputDir) {
        this.baseOutputDir = baseOutputDir != null ? baseOutputDir : Paths.get("data", "parquet");
    }

    /**
     * Converts a daily WAL file into partitioned feature files.
     */
    public ParquetBatchExportReport convert(Path rawWalFile, LocalDate tradeDate) throws IOException {
        long startTime = System.currentTimeMillis();
        long rawFrames = 0L;
        long totalRows = 0L;
        long corruptedFrames = 0L;
        List<Path> partitionFiles = new ArrayList<>();

        if (rawWalFile == null || !Files.exists(rawWalFile)) {
            throw new FileNotFoundException("WAL file does not exist: " + rawWalFile);
        }

        MultiGridClockDispatcher dispatcher = new MultiGridClockDispatcher();
        Map<Long, ForwardReturnTargetEngine> targetEngines = new HashMap<>();
        Map<Long, TradeStrengthClassifier> tradeClassifiers = new HashMap<>();
        Map<Long, CanonicalMarketSnapshot> prevSnapshots = new HashMap<>();

        // Grouping: token -> (intervalMs -> List<ParquetFeatureRecord>)
        Map<Long, Map<Long, List<ParquetFeatureRecord>>> featureBuffers = new HashMap<>();

        // 1. Ingest all WAL frames
        try (FileChannel channel = FileChannel.open(rawWalFile, StandardOpenOption.READ)) {
            ByteBuffer channelBuf = ByteBuffer.allocate(64 * 1024);

            while (channel.position() < channel.size()) {
                channelBuf.clear();
                long startPos = channel.position();
                int read = channel.read(channelBuf);
                if (read < BinaryWALFrame.HEADER_SIZE) break;

                channelBuf.flip();
                BinaryWALFrame frame;
                try {
                    frame = BinaryWALSerializer.deserialize(channelBuf);
                } catch (Exception e) {
                    corruptedFrames++;
                    break;
                }

                // Advance channel to the exact end of this frame
                channel.position(startPos + frame.totalFrameSize());

                // Decode to Canonical snapshot
                CanonicalMarketSnapshot snap = decodeSnapshot(
                        frame.payload(), frame.connectionId(), frame.globalSequence(), frame.monoRecvNanos(), frame.epochRecvMicros()
                );
                if (snap == null) continue;

                rawFrames++;
                long token = snap.instrumentToken();

                CanonicalMarketSnapshot prevSnap = prevSnapshots.get(token);
                TradeStrengthClassifier tradeClassifier = tradeClassifiers.computeIfAbsent(token, k -> new TradeStrengthClassifier());
                tradeClassifier.recordTrade(snap, snap.ltq());

                ForwardReturnTargetEngine targetEngine = targetEngines.computeIfAbsent(token, k -> new ForwardReturnTargetEngine());

                // Feed to multi-grid dispatcher
                Map<Long, List<ResampledGridPoint>> emittedGrids = dispatcher.onTick(snap);

                for (Map.Entry<Long, List<ResampledGridPoint>> entry : emittedGrids.entrySet()) {
                    long intervalMs = entry.getKey();
                    for (ResampledGridPoint pt : entry.getValue()) {
                        targetEngine.appendGridPoint(pt);

                        // Calculate forward target if available
                        ForwardReturnTargets targets = targetEngine.computeTargetsIfMatured(Math.max(0, targetEngine.getHistorySize() - 65));

                        ParquetFeatureRecord record = FeatureVectorAssembler.assemble(
                                pt, prevSnap, tradeClassifier, targets,
                                CostModelRepository.getSchedule(tradeDate), 0.0004
                        );

                        featureBuffers.computeIfAbsent(token, k -> new HashMap<>())
                                      .computeIfAbsent(intervalMs, k -> new ArrayList<>())
                                      .add(record);
                        totalRows++;
                    }
                }

                prevSnapshots.put(token, snap);
            }
        }

        // 2. Write partitioned files: data/parquet/date=YYYY-MM-DD/instrument_token=XXXXX/features_{interval}ms.csv
        for (Map.Entry<Long, Map<Long, List<ParquetFeatureRecord>>> tokenEntry : featureBuffers.entrySet()) {
            long token = tokenEntry.getKey();
            Path tokenDir = baseOutputDir.resolve("date=" + tradeDate.toString())
                                         .resolve("instrument_token=" + token);
            Files.createDirectories(tokenDir);

            for (Map.Entry<Long, List<ParquetFeatureRecord>> intervalEntry : tokenEntry.getValue().entrySet()) {
                long intervalMs = intervalEntry.getKey();
                List<ParquetFeatureRecord> records = intervalEntry.getValue();
                Path file = tokenDir.resolve("features_" + intervalMs + "ms.csv");

                writeFeaturesToFile(file, records);
                partitionFiles.add(file);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new ParquetBatchExportReport(tradeDate, rawFrames, totalRows, corruptedFrames, elapsed, partitionFiles);
    }

    private void writeFeaturesToFile(Path file, List<ParquetFeatureRecord> records) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            // Header
            writer.write("grid_seq,grid_nanos,delta_nanos,instrument_token,symbol,snapshot_age_ms," +
                    "best_bid,best_ask,mid_price,spread,rel_spread_bps,ltp,cum_volume,vwap," +
                    "l1_obi,total_obi,w_obi_lin,w_obi_exp,microprice,micro_pressure,micro_pressure_bps,ml_microprice," +
                    "l1_ofi,ml_ofi_uniform,ml_ofi_exp,trade_strength,buy_pressure,sell_pressure," +
                    "r_1s,r_5s,r_10s,r_30s,r_60s,y_1s,y_5s,y_10s,y_30s,y_60s," +
                    "exec_1s,exec_5s,exec_10s,exec_30s,exec_60s\n");

            for (ParquetFeatureRecord r : records) {
                writer.write(String.format(Locale.US,
                        "%d,%d,%d,%d,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                        r.gridSequence(), r.gridNanos(), r.deltaIntervalNanos(), r.instrumentToken(), r.symbol(), r.snapshotAgeMs(),
                        r.bestBidPrice(), r.bestAskPrice(), r.midPrice(), r.spread(), r.relativeSpreadBps(), r.ltp(), r.cumulativeVolume(), r.dayVwap(),
                        r.level1OBI(), r.totalOBI(), r.weightedOBILinear(), r.weightedOBIExp(), r.level1Microprice(), r.micropricePressure(), r.micropricePressureBps(), r.multiLevelMicroprice(),
                        r.level1OFI(), r.multiLevelOFIUniform(), r.multiLevelOFIExp(), r.tradeStrength(), r.buyPressure(), r.sellPressure(),
                        r.return1s(), r.return5s(), r.return10s(), r.return30s(), r.return60s(),
                        r.label1s(), r.label5s(), r.label10s(), r.label30s(), r.label60s(),
                        r.execLongReturn1s(), r.execLongReturn5s(), r.execLongReturn10s(), r.execLongReturn30s(), r.execLongReturn60s()
                ));
            }
        }
    }

    private CanonicalMarketSnapshot decodeSnapshot(byte[] payload, short feedId, long seq, long arrivalNanos, long epochMicros) {
        if (payload.length < 40) return null;
        ByteBuffer buf = ByteBuffer.wrap(payload);

        long token = buf.getLong();
        double ltp = buf.getDouble();
        long ltq = buf.getLong();
        long volume = buf.getLong();
        double vwap = buf.getDouble();

        return new CanonicalMarketSnapshot(
                "WAL", token, "TOKEN_" + token,
                arrivalNanos, epochMicros, epochMicros / 1_000_000L,
                ltp, ltq, volume, vwap, 1,
                new double[]{ltp - 0.25}, new long[]{100}, new int[]{1},
                new double[]{ltp + 0.25}, new long[]{100}, new int[]{1},
                com.lobmatrix.core.model.BookStateTag.NORMAL
        );
    }
}
