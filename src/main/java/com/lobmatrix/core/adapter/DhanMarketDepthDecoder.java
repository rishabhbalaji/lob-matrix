package com.lobmatrix.core.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * High-performance parser for Dhan WebSocket 20-level market depth packets.
 * Decodes 20 bid and 20 ask depth levels into an immutable CanonicalMarketSnapshot.
 */
public class DhanMarketDepthDecoder {

    private static final Logger log = LoggerFactory.getLogger(DhanMarketDepthDecoder.class);
    public static final int DEPTH_LEVELS = 20;
    private final ObjectMapper objectMapper;
    private final Map<Long, String> symbolLookup;

    public DhanMarketDepthDecoder(Map<Long, String> symbolLookup) {
        this.objectMapper = new ObjectMapper();
        this.symbolLookup = symbolLookup != null ? symbolLookup : Map.of();
    }

    /**
     * Decodes a Dhan JSON depth packet into an immutable CanonicalMarketSnapshot with 20 levels.
     *
     * @param jsonBytes Raw UTF-8 JSON payload
     * @param arrivalNanos Monotonic nano timestamp captured at socket arrival
     * @param arrivalMicros Epoch micro timestamp captured at socket arrival
     * @return CanonicalMarketSnapshot with 20 levels
     */
    public CanonicalMarketSnapshot decode(byte[] jsonBytes, long arrivalNanos, long arrivalMicros) throws IOException {
        JsonNode root = objectMapper.readTree(jsonBytes);

        long token = root.path("securityId").asLong();
        double ltp = root.path("LTP").asDouble();
        long ltq = root.path("LTQ").asLong();
        double dayVwap = root.path("avgPrice").asDouble();
        long cumulativeVolume = root.path("volume").asLong();
        long exchangeTimestamp = root.path("exchangeTime").asLong();

        double[] bidPrices = new double[DEPTH_LEVELS];
        long[] bidQuantities = new long[DEPTH_LEVELS];
        int[] bidOrders = new int[DEPTH_LEVELS];

        JsonNode bidsNode = root.path("depth").path("buy");
        if (bidsNode.isArray()) {
            int count = Math.min(bidsNode.size(), DEPTH_LEVELS);
            for (int i = 0; i < count; i++) {
                JsonNode level = bidsNode.get(i);
                bidPrices[i] = level.path("price").asDouble();
                bidQuantities[i] = level.path("quantity").asLong();
                bidOrders[i] = level.path("orders").asInt();
            }
        }

        double[] askPrices = new double[DEPTH_LEVELS];
        long[] askQuantities = new long[DEPTH_LEVELS];
        int[] askOrders = new int[DEPTH_LEVELS];

        JsonNode asksNode = root.path("depth").path("sell");
        if (asksNode.isArray()) {
            int count = Math.min(asksNode.size(), DEPTH_LEVELS);
            for (int i = 0; i < count; i++) {
                JsonNode level = asksNode.get(i);
                askPrices[i] = level.path("price").asDouble();
                askQuantities[i] = level.path("quantity").asLong();
                askOrders[i] = level.path("orders").asInt();
            }
        }

        String symbol = symbolLookup.getOrDefault(token, "TOKEN_" + token);
        BookStateTag stateTag = CanonicalMarketSnapshot.evaluateStateTag(bidPrices, askPrices);

        return new CanonicalMarketSnapshot(
                "DHAN",
                token,
                symbol,
                arrivalNanos,
                arrivalMicros,
                exchangeTimestamp,
                ltp,
                ltq,
                cumulativeVolume,
                dayVwap,
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
