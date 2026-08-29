package com.lobmatrix.wal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncBinaryWALWriterTest {

    @Test
    @DisplayName("Verify AsyncBinaryWALWriter writes 1000 frames asynchronously and persists with CRC integrity")
    void testAsyncWritingAndIntegrity(@TempDir Path tempDir) throws Exception {
        short connectionId = 1;
        int totalFrames = 1000;

        try (AsyncBinaryWALWriter writer = new AsyncBinaryWALWriter(tempDir, connectionId, 10_000)) {
            writer.start();

            for (int i = 0; i < totalFrames; i++) {
                byte[] payload = ("TICK_PAYLOAD_" + i).getBytes(StandardCharsets.UTF_8);
                long arrivalNanos = 1_000_000L + i;
                long arrivalMicros = 1_700_000_000_000L + i;

                boolean appended = writer.append(arrivalNanos, arrivalMicros, payload);
                assertThat(appended).isTrue();
            }

            assertThat(writer.getWrittenSequenceCount()).isEqualTo(totalFrames);
            Path writtenFile = writer.getCurrentFilePath();
            assertThat(Files.exists(writtenFile)).isTrue();
        }

        // After close, verify file contents by deserializing all 1000 envelopes
        Path dateDir = Files.list(tempDir).findFirst().orElseThrow();
        Path rawFile = Files.list(dateDir).findFirst().orElseThrow();

        byte[] allBytes = Files.readAllBytes(rawFile);
        ByteBuffer buffer = ByteBuffer.wrap(allBytes);

        int verifiedCount = 0;
        while (buffer.hasRemaining()) {
            BinaryWALFrame frame = BinaryWALSerializer.deserialize(buffer);
            assertThat(frame.magicBytes()).isEqualTo(BinaryWALFrame.MAGIC_BYTES);
            assertThat(frame.globalSequence()).isEqualTo(verifiedCount);
            
            String payloadStr = new String(frame.payload(), StandardCharsets.UTF_8);
            assertThat(payloadStr).isEqualTo("TICK_PAYLOAD_" + verifiedCount);
            verifiedCount++;
        }

        assertThat(verifiedCount).isEqualTo(totalFrames);
    }
}
