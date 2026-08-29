package com.lobmatrix.wal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.zip.CRC32;

/**
 * Enterprise crash recovery engine for .raw binary WAL logs.
 * Validates 40-byte framing and CRC-32 integrity, isolates corrupted bytes, and truncates trailing partial writes.
 */
public class WALCrashRecoveryEngine {

    private static final Logger log = LoggerFactory.getLogger(WALCrashRecoveryEngine.class);
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB scan buffer

    /**
     * Scans a .raw WAL file and performs in-place recovery by truncating at the last valid envelope.
     *
     * @param rawFilePath Path to .raw file
     * @return WALRecoveryReport detailing scan and recovery metrics
     */
    public static WALRecoveryReport recoverAndRepair(Path rawFilePath) throws IOException {
        if (!Files.exists(rawFilePath)) {
            throw new NoSuchFileException("WAL file does not exist: " + rawFilePath);
        }

        long originalSize = Files.size(rawFilePath);
        if (originalSize == 0) {
            return new WALRecoveryReport(rawFilePath, 0, 0, 0, false, "EMPTY_FILE");
        }

        long validBytesOffset = 0;
        long validFramesCount = 0;
        boolean corruptionDetected = false;
        String diagnosis = "HEALTHY";

        try (FileChannel channel = FileChannel.open(rawFilePath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            long channelPosition = 0;

            while (channelPosition < originalSize) {
                channel.position(channelPosition);
                buffer.clear();
                int bytesRead = channel.read(buffer);
                if (bytesRead <= 0) break;
                buffer.flip();

                while (buffer.hasRemaining()) {
                    // Check if at least 40-byte header is present
                    if (buffer.remaining() < BinaryWALFrame.HEADER_SIZE) {
                        corruptionDetected = true;
                        diagnosis = String.format("PARTIAL_HEADER_AT_OFFSET_%d", channelPosition + buffer.position());
                        break;
                    }

                    int frameStartPos = buffer.position();
                    int magic = buffer.getInt();
                    if (magic != BinaryWALFrame.MAGIC_BYTES) {
                        corruptionDetected = true;
                        diagnosis = String.format("INVALID_MAGIC_0x%08X_AT_OFFSET_%d", magic, channelPosition + frameStartPos);
                        break;
                    }

                    short version = buffer.getShort();
                    short connectionId = buffer.getShort();
                    long sequence = buffer.getLong();
                    long monoNanos = buffer.getLong();
                    long epochMicros = buffer.getLong();
                    int payloadLength = buffer.getInt();
                    long expectedCrc = buffer.getInt() & 0xFFFFFFFFL;

                    if (payloadLength < 0 || payloadLength > (10 * 1024 * 1024)) { // Max 10MB sanity check
                        corruptionDetected = true;
                        diagnosis = String.format("INVALID_PAYLOAD_LENGTH_%d_AT_OFFSET_%d", payloadLength, channelPosition + frameStartPos);
                        break;
                    }

                    // Check if full payload is present in buffer
                    if (buffer.remaining() < payloadLength) {
                        // If remaining buffer is less than payload, we need to check if file has the rest
                        long remainingInFile = originalSize - (channelPosition + buffer.position());
                        if (remainingInFile < payloadLength) {
                            corruptionDetected = true;
                            diagnosis = String.format("TRUNCATED_PAYLOAD_NEED_%d_FOUND_%d_AT_OFFSET_%d", 
                                    payloadLength, remainingInFile, channelPosition + buffer.position());
                            break;
                        }

                        // Refill buffer positioned at frame start and re-read
                        channelPosition += frameStartPos;
                        channel.position(channelPosition);
                        buffer.clear();
                        channel.read(buffer);
                        buffer.flip();
                        continue;
                    }

                    byte[] payload = new byte[payloadLength];
                    buffer.get(payload);

                    // Validate CRC-32
                    CRC32 crc = new CRC32();
                    crc.update(payload);
                    long computedCrc = crc.getValue();

                    if (computedCrc != expectedCrc) {
                        corruptionDetected = true;
                        diagnosis = String.format("CRC_MISMATCH_EXPECTED_0x%08X_COMPUTED_0x%08X_AT_OFFSET_%d", 
                                expectedCrc, computedCrc, channelPosition + frameStartPos);
                        break;
                    }

                    // Frame is 100% valid
                    validFramesCount++;
                    validBytesOffset = channelPosition + buffer.position();
                }

                if (corruptionDetected) {
                    break;
                }
                channelPosition += buffer.position();
            }

            // If corruption or partial tail detected, truncate file in-place
            if (corruptionDetected || validBytesOffset < originalSize) {
                log.warn("Corrupted WAL tail detected in {}. Truncating from {} bytes down to {} valid bytes.",
                        rawFilePath.getFileName(), originalSize, validBytesOffset);
                channel.truncate(validBytesOffset);
                channel.force(true);
            }
        }

        long recoveredSize = Files.size(rawFilePath);
        return new WALRecoveryReport(rawFilePath, originalSize, recoveredSize, validFramesCount, corruptionDetected, diagnosis);
    }
}
