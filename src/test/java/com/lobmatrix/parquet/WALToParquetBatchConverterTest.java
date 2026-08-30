package com.lobmatrix.parquet;

import com.lobmatrix.wal.BinaryWALFrame;
import com.lobmatrix.wal.BinaryWALSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WALToParquetBatchConverterTest {

    @Test
    @DisplayName("Verify WAL-to-Parquet conversion generates clean partitioned files with zero errors")
    void testWALBatchConversion(@TempDir Path tempDir) throws IOException {
        Path walFile = tempDir.resolve("market_feed_test.raw");
        Path parquetOutDir = tempDir.resolve("parquet_out");

        // Write synthetic WAL frames (10 ticks for token 738561)
        try (FileChannel channel = FileChannel.open(walFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < 10; i++) {
                ByteBuffer payload = ByteBuffer.allocate(40);
                payload.putLong(738561L);
                payload.putDouble(2500.0 + i); // LTP
                payload.putLong(10L);          // LTQ
                payload.putLong(1000L + (i * 10L)); // Volume
                payload.putDouble(2500.0);     // VWAP
                byte[] rawPayload = payload.array();

                long arrivalNanos = (i + 1) * 1_000_000_000L; // 1s intervals
                byte[] serialized = BinaryWALSerializer.serialize((short) 1, i + 1, arrivalNanos, arrivalNanos / 1000L, rawPayload);
                channel.write(ByteBuffer.wrap(serialized));
            }
        }

        WALToParquetBatchConverter converter = new WALToParquetBatchConverter(parquetOutDir);
        LocalDate tradeDate = LocalDate.of(2026, 8, 29);
        ParquetBatchExportReport report = converter.convert(walFile, tradeDate);

        assertThat(report.rawFramesProcessed()).isEqualTo(10L);
        assertThat(report.corruptedFramesSkipped()).isEqualTo(0L);
        assertThat(report.totalFeatureRowsGenerated()).isGreaterThan(0L);
        assertThat(report.generatedPartitionFiles()).isNotEmpty();

        // Verify partition directory structure: date=2026-08-29/instrument_token=738561/features_1000ms.csv
        Path expectedDir = parquetOutDir.resolve("date=2026-08-29").resolve("instrument_token=738561");
        assertThat(Files.exists(expectedDir)).isTrue();
        assertThat(Files.exists(expectedDir.resolve("features_1000ms.csv"))).isTrue();
    }
}
