package com.lobmatrix.inference;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.math.TradeStrengthClassifier;
import com.lobmatrix.engine.resample.ResampledGridPoint;
import com.lobmatrix.engine.target.CostModelRepository;
import com.lobmatrix.engine.target.ForwardReturnTargets;
import com.lobmatrix.parquet.FeatureVectorAssembler;
import com.lobmatrix.parquet.ParquetFeatureRecord;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TickInferenceEvaluatorTest {

    private static final Path MODEL_PATH = Path.of("data/models/champion_model.onnx");
    private static final Path METADATA_PATH = Path.of("data/models/modelmetadata.json");
    private static final Path SCALER_PATH = Path.of("data/models/scalerparams.json");

    private static ParquetFeatureRecord sampleRecord(long gridSeq) {
        double[] bids = {1000.0, 999.5, 999.0, 998.5, 998.0};
        double[] asks = {1001.0, 1001.5, 1002.0, 1002.5, 1003.0};
        long[] bidQty = {100, 200, 300, 400, 500};
        long[] askQty = {120, 200, 300, 400, 500};
        int[] orders = {1, 1, 1, 1, 1};

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 738561L, "RELIANCE",
                1_000_000_000L + gridSeq, 1_000_000L, 1700000000L,
                1000.5, 10, 500000, 1000.0, 5,
                bids, bidQty, orders, asks, askQty, orders, BookStateTag.NORMAL
        );

        ResampledGridPoint gridPoint = new ResampledGridPoint(
                gridSeq, 1_000_000_000L + gridSeq, 1_000_000_000L, snapshot, 5_000_000L
        );

        ForwardReturnTargets forwardTargets = new ForwardReturnTargets(
                1_000_000_000L, 1000.5,
                0.0004, 0.0008, 0.0012, 0.0020, 0.0030
        );

        TradeStrengthClassifier tradeClassifier = new TradeStrengthClassifier();
        tradeClassifier.recordTrade(snapshot, 100);

        return FeatureVectorAssembler.assemble(
                gridPoint, snapshot, tradeClassifier, forwardTargets,
                CostModelRepository.getDefaultSchedule(), 0.0004
        );
    }

    @Test
    void evaluatesLiveFeatureRecordAndProducesFiniteProbabilities() {
        assumeTrue(
                Files.isRegularFile(MODEL_PATH) && Files.isRegularFile(METADATA_PATH)
                        && Files.isRegularFile(SCALER_PATH),
                "M4P3 generated artifacts absent; generate them before runtime verification"
        );

        try (TickInferenceEvaluator evaluator = TickInferenceEvaluator.load(
                MODEL_PATH, METADATA_PATH, SCALER_PATH)) {

            OnnxModelService.Prediction prediction = evaluator.evaluate(sampleRecord(1L));

            assertThat(prediction.predictedOriginalLabel()).isBetween(-1, 1);
            assertThat(prediction.probabilityDown()).isBetween(0.0f, 1.0f);
            assertThat(prediction.probabilityNeutral()).isBetween(0.0f, 1.0f);
            assertThat(prediction.probabilityUp()).isBetween(0.0f, 1.0f);
            assertThat(
                    prediction.probabilityDown()
                            + prediction.probabilityNeutral()
                            + prediction.probabilityUp()
            ).isCloseTo(1.0f, org.assertj.core.data.Offset.offset(1e-4f));
        }
    }

    @Test
    void sustainsTwentyThousandInferencesPerSecondWithBoundedMemory() {
        assumeTrue(
                Files.isRegularFile(MODEL_PATH) && Files.isRegularFile(METADATA_PATH)
                        && Files.isRegularFile(SCALER_PATH),
                "M4P3 generated artifacts absent; generate them before runtime verification"
        );

        try (TickInferenceEvaluator evaluator = TickInferenceEvaluator.load(
                MODEL_PATH, METADATA_PATH, SCALER_PATH)) {

            ParquetFeatureRecord record = sampleRecord(1L);

            // Warm up JIT before measuring throughput.
            for (int i = 0; i < 5_000; i++) {
                evaluator.evaluate(record);
            }

            Runtime runtime = Runtime.getRuntime();
            System.gc();
            long heapBefore = runtime.totalMemory() - runtime.freeMemory();

            int iterations = 40_000;
            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                OnnxModelService.Prediction prediction = evaluator.evaluate(record);
                assertThat(prediction.probabilityUp()).isBetween(0.0f, 1.0f);
            }
            long elapsedNanos = System.nanoTime() - start;

            System.gc();
            long heapAfter = runtime.totalMemory() - runtime.freeMemory();

            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            double inferencesPerSecond = iterations / elapsedSeconds;
            long heapGrowthBytes = heapAfter - heapBefore;
            long heapGrowthMib = heapGrowthBytes / (1024 * 1024);

            System.out.printf(
                    "M5P1S2 throughput: %.0f inferences/sec over %d iterations (%.3fs); heap growth after GC: %d MiB%n",
                    inferencesPerSecond, iterations, elapsedSeconds, heapGrowthMib
            );

            assertThat(inferencesPerSecond).isGreaterThanOrEqualTo(20_000.0);
            assertThat(heapGrowthMib).isLessThan(50);
        }
    }
}
