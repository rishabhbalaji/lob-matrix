package com.lobmatrix.parquet;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Audit metrics summary for post-market WAL-to-Parquet conversion runs.
 */
public record ParquetBatchExportReport(
        LocalDate tradeDate,
        long rawFramesProcessed,
        long totalFeatureRowsGenerated,
        long corruptedFramesSkipped,
        long elapsedMillis,
        List<Path> generatedPartitionFiles
) {}
