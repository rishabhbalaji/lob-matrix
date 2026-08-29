package com.lobmatrix.wal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;

class WALCrashRecoveryEngineTest {

    @Test
    @DisplayName("Verify healthy WAL scan reports zero corruption")
    void testHealthyWALScan(@TempDir Path tempDir) throws Exception {
        Path rawFile = tempDir.resolve("healthy.raw");
        short connectionId = 1;

        // Write 10 clean frames
        for (int i = 0; i < 10; i++) {
            byte[] payload = ("PAYLOAD_" + i).getBytes(StandardCharsets.UTF_8);
            byte[] envelope = BinaryWALSerializer.serialize(connectionId, i, 100L + i, 200L + i, payload);
            Files.write(rawFile, envelope, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        WALRecoveryReport report = WALCrashRecoveryEngine.recoverAndRepair(rawFile);

        assertThat(report.wasCorrupted()).isFalse();
        assertThat(report.validFramesCount()).isEqualTo(10);
        assertThat(report.originalSizeBytes()).isEqualTo(report.recoveredSizeBytes());
        assertThat(report.bytesTruncated()).isEqualTo(0);
    }

    @Test
    @DisplayName("Verify mid-write truncated trailing header is detected and cleanly truncated")
    void testTruncatedHeaderRecovery(@TempDir Path tempDir) throws Exception {
        Path rawFile = tempDir.resolve("truncated_header.raw");

        // Write 5 clean frames
        long expectedCleanBytes = 0;
        for (int i = 0; i < 5; i++) {
            byte[] payload = ("CLEAN_FRAME_" + i).getBytes(StandardCharsets.UTF_8);
            byte[] envelope = BinaryWALSerializer.serialize((short) 1, i, 100L + i, 200L + i, payload);
            Files.write(rawFile, envelope, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            expectedCleanBytes += envelope.length;
        }

        // Simulate crash mid-write: append only 12 bytes of a 40-byte header
        byte[] partialHeader = new byte[]{0x4F, 0x42, 0x41, 0x57, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x05};
        Files.write(rawFile, partialHeader, StandardOpenOption.APPEND);

        assertThat(Files.size(rawFile)).isEqualTo(expectedCleanBytes + 12);

        // Run recovery
        WALRecoveryReport report = WALCrashRecoveryEngine.recoverAndRepair(rawFile);

        assertThat(report.wasCorrupted()).isTrue();
        assertThat(report.validFramesCount()).isEqualTo(5);
        assertThat(report.recoveredSizeBytes()).isEqualTo(expectedCleanBytes);
        assertThat(report.bytesTruncated()).isEqualTo(12);
        assertThat(Files.size(rawFile)).isEqualTo(expectedCleanBytes);
    }

    @Test
    @DisplayName("Verify mid-write truncated payload is detected and cleanly truncated")
    void testTruncatedPayloadRecovery(@TempDir Path tempDir) throws Exception {
        Path rawFile = tempDir.resolve("truncated_payload.raw");

        // Write 3 clean frames
        long expectedCleanBytes = 0;
        for (int i = 0; i < 3; i++) {
            byte[] payload = ("PAYLOAD_CLEAN_" + i).getBytes(StandardCharsets.UTF_8);
            byte[] envelope = BinaryWALSerializer.serialize((short) 1, i, 100L + i, 200L + i, payload);
            Files.write(rawFile, envelope, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            expectedCleanBytes += envelope.length;
        }

        // Write 4th frame with 100-byte payload declared, but only write 20 bytes
        byte[] fullPayload = new byte[100];
        byte[] envelope = BinaryWALSerializer.serialize((short) 1, 3, 300L, 400L, fullPayload);
        // Truncate envelope to header + 20 bytes (missing 80 bytes)
        byte[] partialEnvelope = new byte[40 + 20];
        System.arraycopy(envelope, 0, partialEnvelope, 0, 40 + 20);

        Files.write(rawFile, partialEnvelope, StandardOpenOption.APPEND);

        // Run recovery
        WALRecoveryReport report = WALCrashRecoveryEngine.recoverAndRepair(rawFile);

        assertThat(report.wasCorrupted()).isTrue();
        assertThat(report.validFramesCount()).isEqualTo(3);
        assertThat(report.recoveredSizeBytes()).isEqualTo(expectedCleanBytes);
        assertThat(report.bytesTruncated()).isEqualTo(60);
    }
}
