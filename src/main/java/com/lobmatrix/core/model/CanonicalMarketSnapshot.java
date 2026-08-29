package com.lobmatrix.core.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable canonical market snapshot supporting N-level depth (Top-5, Top-20, Top-200).
 * Enforces strict defensive copying to prevent thread-safety mutability leaks.
 */
public record CanonicalMarketSnapshot(
        String sourceId,            // "ZERODHA", "DHAN", "UPSTOX", "MOCK"
        long instrumentToken,       // Unified internal token ID (e.g. 738561 for RELIANCE)
        String symbol,              // Ticker symbol (e.g. "RELIANCE")
        long clientArrivalNanos,    // System.nanoTime() for causal ordering
        long clientArrivalMicros,   // Epoch microsecond timestamp for storage
        long exchangeEpochSecs,     // Exchange timestamp in integer seconds
        double ltp,                 // Last Traded Price
        long ltq,                   // Last Traded Quantity
        long cumulativeVolume,      // Total day traded volume
        double dayVwap,             // Average Traded Price (ATP / VWAP)
        int depthLevels,            // Number of depth levels (e.g. 5 or 20)
        double[] bidPrices,         // Array of size depthLevels (sorted high to low)
        long[] bidQuantities,       // Array of size depthLevels
        int[] bidOrders,            // Order counts per bid level
        double[] askPrices,         // Array of size depthLevels (sorted low to high)
        long[] askQuantities,       // Array of size depthLevels
        int[] askOrders,            // Order counts per ask level
        BookStateTag stateTag       // NORMAL, CROSSED, LOCKED, EMPTY_SIDE
) {
    // Compact constructor enforcing immutability via defensive cloning
    public CanonicalMarketSnapshot {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(stateTag, "stateTag must not be null");

        // Clone array inputs to prevent caller mutating them
        bidPrices = bidPrices != null ? bidPrices.clone() : new double[0];
        bidQuantities = bidQuantities != null ? bidQuantities.clone() : new long[0];
        bidOrders = bidOrders != null ? bidOrders.clone() : new int[0];
        askPrices = askPrices != null ? askPrices.clone() : new double[0];
        askQuantities = askQuantities != null ? askQuantities.clone() : new long[0];
        askOrders = askOrders != null ? askOrders.clone() : new int[0];
    }

    // Defensive accessor overrides to prevent external modification
    @Override
    public double[] bidPrices() { return bidPrices.clone(); }

    @Override
    public long[] bidQuantities() { return bidQuantities.clone(); }

    @Override
    public int[] bidOrders() { return bidOrders.clone(); }

    @Override
    public double[] askPrices() { return askPrices.clone(); }

    @Override
    public long[] askQuantities() { return askQuantities.clone(); }

    @Override
    public int[] askOrders() { return askOrders.clone(); }

    /**
     * Calculates the midpoint price between Best Bid and Best Ask.
     */
    public double midPrice() {
        if (bidPrices.length > 0 && askPrices.length > 0 && bidPrices[0] > 0 && askPrices[0] > 0) {
            return (bidPrices[0] + askPrices[0]) / 2.0;
        }
        return ltp;
    }

    /**
     * Calculates the top-of-book spread (Best Ask - Best Bid).
     */
    public double spread() {
        if (bidPrices.length > 0 && askPrices.length > 0 && bidPrices[0] > 0 && askPrices[0] > 0) {
            return askPrices[0] - bidPrices[0];
        }
        return 0.0;
    }

    /**
     * Helper factory to create a snapshot with automatic state tag detection.
     */
    public static BookStateTag evaluateStateTag(double[] bidPrices, double[] askPrices) {
        if (bidPrices == null || askPrices == null || bidPrices.length == 0 || askPrices.length == 0 || bidPrices[0] <= 0 || askPrices[0] <= 0) {
            return BookStateTag.STATE_EMPTY_SIDE;
        }
        double bestBid = bidPrices[0];
        double bestAsk = askPrices[0];
        if (bestBid > bestAsk) {
            return BookStateTag.STATE_CROSSED;
        } else if (Double.compare(bestBid, bestAsk) == 0) {
            return BookStateTag.STATE_LOCKED;
        }
        return BookStateTag.NORMAL;
    }
}
