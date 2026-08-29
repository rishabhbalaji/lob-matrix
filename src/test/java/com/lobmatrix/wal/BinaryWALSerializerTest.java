package com.lobmatrix.wal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryWALSerializerTest {

    @Test
    @DisplayName("Verify 40-byte header serialization and bit-for-bit deserialization roundtrip")
    void testSerializationRoundtrip() {
        byte[] payload = "SAMPLE_ZERODHA_BINARY_PAYLOAD_184_BYTES".getBytes(StandardCharsets.UTF_8);
        short connectionId = 1;
        long sequence = 100_001L;
        long monoNanos = 123_456_789L;
        long epochMicros = 1_700_000_000_000_000L;

        byte[] envelope = BinaryWALSerializer.serialize(connectionId, sequence, monoNanos, epochMicros, payload);

        // Header (40B) + Payload Length
        assertThat(envelope.length).isEqualTo(40 + payload.length);

        // Deserialize
        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        BinaryWALFrame frame = BinaryWALSerializer.deserialize(buffer);

        assertThat(frame.magicBytes()).isEqualTo(BinaryWALFrame.MAGIC_BYTES);
        assertThat(frame.version()).isEqualTo(BinaryWALFrame.CURRENT_VERSION);
        assertThat(frame.connectionId()).isEqualTo(connectionId);
        assertThat(frame.globalSequence()).isEqualTo(sequence);
        assertThat(frame.monoRecvNanos()).isEqualTo(monoNanos);
        assertThat(frame.epochRecvMicros()).isEqualTo(epochMicros);
        assertThat(frame.payloadLength()).isEqualTo(payload.length);
        assertThat(frame.payload()).containsExactly(payload);
    }

    @Test
    @DisplayName("Verify CRC-32 checksum catches corrupted payload bytes")
    void testChecksumCatchesCorruption() {
        byte[] payload = "UNTOUCHED_MARKET_DATA".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = BinaryWALSerializer.serialize((short) 1, 1L, 100L, 200L, payload);

        // Corrupt 1 byte in the payload area (byte 45)
        envelope[45] = (byte) (envelope[45] ^ 0xFF);

        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        assertThatThrownBy(() -> BinaryWALSerializer.deserialize(buffer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRC-32 mismatch");
    }

    @Test
    @DisplayName("Verify parser rejects invalid magic bytes")
    void testInvalidMagicBytes() {
        byte[] payload = "DATA".getBytes(StandardCharsets.UTF_8);
        byte[] envelope = BinaryWALSerializer.serialize((short) 1, 1L, 100L, 200L, payload);

        // Corrupt magic bytes
        envelope[0] = 0x00;

        ByteBuffer buffer = ByteBuffer.wrap(envelope);
        assertThatThrownBy(() -> BinaryWALSerializer.deserialize(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid WAL magic bytes");
    }
}
