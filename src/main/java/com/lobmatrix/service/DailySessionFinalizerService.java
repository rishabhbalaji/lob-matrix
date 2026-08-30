package com.lobmatrix.service;

import com.lobmatrix.parquet.ParquetBatchExportReport;
import com.lobmatrix.parquet.WALToParquetBatchConverter;
import com.lobmatrix.persistence.entity.SessionMetadataEntity;
import com.lobmatrix.persistence.repository.SessionMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service orchestrating post-market finalization (15:45 IST):
 * Runs batch WAL-to-Parquet conversion and persists session audit metadata to the database.
 */
@Service
public class DailySessionFinalizerService {

    private static final Logger log = LoggerFactory.getLogger(DailySessionFinalizerService.class);

    private final SessionMetadataRepository sessionMetadataRepository;
    private final Path rawWalDir;
    private final Path parquetDir;

    public DailySessionFinalizerService(
            SessionMetadataRepository sessionMetadataRepository,
            @Value("${lobmatrix.storage.raw-wal-dir:data/raw}") String rawWalDir,
            @Value("${lobmatrix.storage.parquet-dir:data/parquet}") String parquetDir
    ) {
        this.sessionMetadataRepository = sessionMetadataRepository;
        this.rawWalDir = Paths.get(rawWalDir);
        this.parquetDir = Paths.get(parquetDir);
    }

    /**
     * Finalizes a daily market session: converts WAL to Parquet and saves audit metadata to SQL.
     */
    public SessionMetadataEntity finalizeSession(LocalDate tradeDate, String feedSource, Path rawWalFile) throws IOException {
        log.info("Starting Daily Market Session Finalization for date={}, source={}", tradeDate, feedSource);
        LocalDateTime startTime = LocalDateTime.now();

        WALToParquetBatchConverter converter = new WALToParquetBatchConverter(parquetDir);
        ParquetBatchExportReport report = converter.convert(rawWalFile, tradeDate);

        LocalDateTime endTime = LocalDateTime.now();

        SessionMetadataEntity metadata = sessionMetadataRepository.findByTradeDate(tradeDate)
                .orElse(new SessionMetadataEntity());

        metadata.setTradeDate(tradeDate);
        metadata.setFeedSource(feedSource != null ? feedSource : "MOCK");
        metadata.setStartTime(startTime);
        metadata.setEndTime(endTime);
        metadata.setTotalRawFrames(report.rawFramesProcessed());
        metadata.setTotalParquetRows(report.totalFeatureRowsGenerated());
        metadata.setCorruptedFramesSkipped(report.corruptedFramesSkipped());
        metadata.setStatus("FINALIZED");

        SessionMetadataEntity saved = sessionMetadataRepository.save(metadata);
        log.info("Session finalization successful! Persisted metadata ID={}, totalRows={}", saved.getId(), saved.getTotalParquetRows());
        return saved;
    }
}
