package com.lobmatrix.service;

import com.lobmatrix.persistence.entity.SessionMetadataEntity;
import com.lobmatrix.persistence.repository.SessionMetadataRepository;
import com.lobmatrix.wal.BinaryWALSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DailySessionFinalizerServiceTest {

    @Autowired
    private SessionMetadataRepository sessionMetadataRepository;

    @Test
    @DisplayName("Verify DailySessionFinalizerService executes batch conversion and saves metadata to SQL")
    void testSessionFinalizerExecution(@TempDir Path tempDir) throws IOException {
        Path walFile = tempDir.resolve("market_feed_20260829.raw");

        // Write synthetic WAL frames
        try (FileChannel channel = FileChannel.open(walFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < 5; i++) {
                ByteBuffer payload = ByteBuffer.allocate(40);
                payload.putLong(738561L);
                payload.putDouble(2500.0 + i);
                payload.putLong(10L);
                payload.putLong(1000L + (i * 10L));
                payload.putDouble(2500.0);
                byte[] raw = payload.array();

                long arrivalNanos = (i + 1) * 1_000_000_000L;
                byte[] frame = BinaryWALSerializer.serialize((short) 1, i + 1, arrivalNanos, arrivalNanos / 1000L, raw);
                channel.write(ByteBuffer.wrap(frame));
            }
        }

        DailySessionFinalizerService service = new DailySessionFinalizerService(
                sessionMetadataRepository,
                tempDir.toString(),
                tempDir.resolve("parquet").toString()
        );

        LocalDate tradeDate = LocalDate.of(2026, 8, 29);
        SessionMetadataEntity result = service.finalizeSession(tradeDate, "ZERODHA", walFile);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getTradeDate()).isEqualTo(tradeDate);
        assertThat(result.getTotalRawFrames()).isEqualTo(5L);
        assertThat(result.getStatus()).isEqualTo("FINALIZED");

        // Verify retrieval from DB
        SessionMetadataEntity retrieved = sessionMetadataRepository.findByTradeDate(tradeDate).orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getFeedSource()).isEqualTo("ZERODHA");
    }
}
