package com.lobmatrix.wal;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a discrete, framed market data envelope inside the append-only .raw WAL file.
 */
public record BinaryWALFrame(
        int magicBytes,             // 0x4F424157 ("OBAW")
        short version,              // Format version (currently 1)
        short connectionId,         // Connection session identifier
        long globalSequence,        // Monotonically increasing sequence number
        long monoRecvNanos,         // System.nanoTime() captured at network socket arrival
        long epochRecvMicros,       // Instant.now() wall-clock microseconds
        int payloadLength,          // Length of payload bytes (L)
        long payloadCrc32,          // Unsigned 32-bit CRC checksum of payload
        byte[] payload              // Raw binary broker payload (e.g. 184 bytes for Zerodha)
) {
    public static final int MAGIC_BYTES = 0x4F424157; // "OBAW" in ASCII hex
    public static final short CURRENT_VERSION = 1;
    public static final int HEADER_SIZE = 40;

    public BinaryWALFrame {
        Objects.requireNonNull(payload, "payload must not be null");
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    public int totalFrameSize() {
        return HEADER_SIZE + payloadLength;
    }
}
