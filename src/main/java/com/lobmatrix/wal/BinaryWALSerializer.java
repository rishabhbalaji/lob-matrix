package com.lobmatrix.wal;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * Ultra-fast serializer and deserializer for 40-byte framed Binary WAL envelopes.
 */
public class BinaryWALSerializer {

    /**
     * Serializes a payload into a full 40-byte framed envelope (40 + L bytes).
     */
    public static byte[] serialize(short connectionId, long sequence, long monoNanos, long epochMicros, byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }

        int payloadLength = payload.length;
        byte[] output = new byte[BinaryWALFrame.HEADER_SIZE + payloadLength];
        ByteBuffer buffer = ByteBuffer.wrap(output);

        // Compute hardware-accelerated CRC-32
        CRC32 crc = new CRC32();
        crc.update(payload);
        long crcValue = crc.getValue(); // Unsigned 32-bit int

        // Write 40-Byte Header (Big-Endian)
        buffer.putInt(BinaryWALFrame.MAGIC_BYTES);      // 00-03: 0x4F424157
        buffer.putShort(BinaryWALFrame.CURRENT_VERSION);// 04-05: Version 1
        buffer.putShort(connectionId);                  // 06-07: Connection ID
        buffer.putLong(sequence);                       // 08-15: Sequence
        buffer.putLong(monoNanos);                      // 16-23: Monotonic Nanos
        buffer.putLong(epochMicros);                    // 24-31: Epoch Micros
        buffer.putInt(payloadLength);                   // 32-35: Length L
        buffer.putInt((int) crcValue);                  // 36-39: CRC-32

        // Write Payload (40 to 40+L)
        buffer.put(payload);

        return output;
    }

    /**
     * Deserializes a BinaryWALFrame from a ByteBuffer.
     *
     * @param buffer ByteBuffer positioned at the start of an envelope
     * @return BinaryWALFrame instance
     */
    public static BinaryWALFrame deserialize(ByteBuffer buffer) {
        if (buffer.remaining() < BinaryWALFrame.HEADER_SIZE) {
            throw new IllegalArgumentException("Insufficient bytes for WAL header. Need at least 40 bytes.");
        }

        int magic = buffer.getInt();
        if (magic != BinaryWALFrame.MAGIC_BYTES) {
            throw new IllegalArgumentException(String.format("Invalid WAL magic bytes: 0x%08X (expected 0x%08X)", magic, BinaryWALFrame.MAGIC_BYTES));
        }

        short version = buffer.getShort();
        short connectionId = buffer.getShort();
        long sequence = buffer.getLong();
        long monoNanos = buffer.getLong();
        long epochMicros = buffer.getLong();
        int payloadLength = buffer.getInt();
        long crcValue = buffer.getInt() & 0xFFFFFFFFL;

        if (buffer.remaining() < payloadLength) {
            throw new IllegalArgumentException(String.format("Partial frame detected. Expected %d payload bytes, but only %d available.", payloadLength, buffer.remaining()));
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);

        // Verify CRC-32
        CRC32 crc = new CRC32();
        crc.update(payload);
        long computedCrc = crc.getValue();

        if (computedCrc != crcValue) {
            throw new IllegalStateException(String.format("CRC-32 mismatch! Corrupted frame at sequence %d. Expected 0x%08X, computed 0x%08X", sequence, crcValue, computedCrc));
        }

        return new BinaryWALFrame(
                magic, version, connectionId, sequence,
                monoNanos, epochMicros, payloadLength, crcValue, payload
        );
    }
}
