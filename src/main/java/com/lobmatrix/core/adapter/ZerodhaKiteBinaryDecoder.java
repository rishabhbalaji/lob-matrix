package com.lobmatrix.core.adapter;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * High-performance zero-copy binary parser for Zerodha Kite WebSocket packets.
 * Decodes 184-byte 'full' mode packets containing Top-5 market depth.
 */
public class ZerodhaKiteBinaryDecoder {

    private static final Logger log = LoggerFactory.getLogger(ZerodhaKiteBinaryDecoder.class);
    public static final int FULL_PACKET_LENGTH = 184;
    private static final double PRICE_DIVISOR = 100.0;
    private static final int DEPTH_LEVELS = 5;

    private final Map<Long, String> symbolLookup;

    public ZerodhaKiteBinaryDecoder(Map<Long, String> symbolLookup) {
        this.symbolLookup = symbolLookup != null ? symbolLookup : Map.of();
    }

    /**
     * Decodes a 184-byte binary Kite packet into an immutable CanonicalMarketSnapshot.
     */
    public CanonicalMarketSnapshot decode(byte[] rawBytes, int offset, long arrivalNanos, long arrivalMicros) {
        if (rawBytes == null || rawBytes.length - offset < FULL_PACKET_LENGTH) {
            throw new IllegalArgumentException("Invalid Kite binary packet length. Expected at least 184 bytes, got: " 
                    + (rawBytes == null ? 0 : rawBytes.length - offset));
        }

        ByteBuffer buffer = ByteBuffer.wrap(rawBytes, offset, FULL_PACKET_LENGTH);

        // Header fields (0 - 63)
        long token = buffer.getInt() & 0xFFFFFFFFL; // Unsigned 32-bit int
        double ltp = buffer.getInt() / PRICE_DIVISOR;
        long ltq = buffer.getInt() & 0xFFFFFFFFL;
        double atp = buffer.getInt() / PRICE_DIVISOR;
        long volume = buffer.getInt() & 0xFFFFFFFFL;
        long totalBuyQty = buffer.getInt() & 0xFFFFFFFFL;
        long totalSellQty = buffer.getInt() & 0xFFFFFFFFL;

        // Skip OHLC (16 bytes), Last Trade Time (4 bytes), OI fields (12 bytes)
        buffer.position(buffer.position() + 16 + 4 + 12);

        long exchangeTimestamp = buffer.getInt() & 0xFFFFFFFFL;

        // 5 Bid Levels (64 - 123)
        double[] bidPrices = new double[DEPTH_LEVELS];
        long[] bidQuantities = new long[DEPTH_LEVELS];
        int[] bidOrders = new int[DEPTH_LEVELS];

        for (int i = 0; i < DEPTH_LEVELS; i++) {
            bidQuantities[i] = buffer.getInt() & 0xFFFFFFFFL;
            bidPrices[i] = buffer.getInt() / PRICE_DIVISOR;
            bidOrders[i] = buffer.getShort() & 0xFFFF;
            buffer.getShort(); // Skip 2 bytes padding
        }

        // 5 Ask Levels (124 - 183)
        double[] askPrices = new double[DEPTH_LEVELS];
        long[] askQuantities = new long[DEPTH_LEVELS];
        int[] askOrders = new int[DEPTH_LEVELS];

        for (int i = 0; i < DEPTH_LEVELS; i++) {
            askQuantities[i] = buffer.getInt() & 0xFFFFFFFFL;
            askPrices[i] = buffer.getInt() / PRICE_DIVISOR;
            askOrders[i] = buffer.getShort() & 0xFFFF;
            buffer.getShort(); // Skip 2 bytes padding
        }

        String symbol = symbolLookup.getOrDefault(token, "TOKEN_" + token);
        BookStateTag stateTag = CanonicalMarketSnapshot.evaluateStateTag(bidPrices, askPrices);

        return new CanonicalMarketSnapshot(
                "ZERODHA",
                token,
                symbol,
                arrivalNanos,
                arrivalMicros,
                exchangeTimestamp,
                ltp,
                ltq,
                volume,
                atp,
                DEPTH_LEVELS,
                bidPrices,
                bidQuantities,
                bidOrders,
                askPrices,
                askQuantities,
                askOrders,
                stateTag
        );
    }
}
