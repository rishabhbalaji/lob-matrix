package com.lobmatrix.engine.state;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;

import java.util.Arrays;

/**
 * Mutable, zero-allocation container representing the live order book state of a single instrument.
 * Owned exclusively by the OrderBookStateManager thread to ensure thread safety without locks.
 */
public class MutableOrderBookState {

    private final long instrumentToken;
    private String symbol;
    private String sourceId;
    private int depthLevels;

    // Dual Clocks
    private long lastArrivalNanos;
    private long lastArrivalMicros;
    private long exchangeTimestamp;

    // Price & Trade Flow
    private double ltp;
    private long ltq;
    private long cumulativeVolume;
    private double dayVwap;

    // Fixed-size depth arrays
    private double[] bidPrices;
    private long[] bidQuantities;
    private int[] bidOrders;
    private double[] askPrices;
    private long[] askQuantities;
    private int[] askOrders;

    // Anomaly & State Tracking
    private BookStateTag stateTag = BookStateTag.STATE_EMPTY_SIDE;
    private long crossedStateStartNanos = 0L;
    private long totalCrossedDurationNanos = 0L;
    private long updateCount = 0L;

    public MutableOrderBookState(long instrumentToken, int initialDepthLevels) {
        this.instrumentToken = instrumentToken;
        this.depthLevels = initialDepthLevels > 0 ? initialDepthLevels : 5;
        this.bidPrices = new double[this.depthLevels];
        this.bidQuantities = new long[this.depthLevels];
        this.bidOrders = new int[this.depthLevels];
        this.askPrices = new double[this.depthLevels];
        this.askQuantities = new long[this.depthLevels];
        this.askOrders = new int[this.depthLevels];
    }

    /**
     * Updates internal state in-place with zero heap allocations.
     */
    public void update(CanonicalMarketSnapshot snapshot) {
        if (snapshot == null || snapshot.instrumentToken() != this.instrumentToken) {
            return;
        }

        this.symbol = snapshot.symbol();
        this.sourceId = snapshot.sourceId();
        this.lastArrivalNanos = snapshot.clientArrivalNanos();
        this.lastArrivalMicros = snapshot.clientArrivalMicros();
        this.exchangeTimestamp = snapshot.exchangeEpochSecs();
        this.ltp = snapshot.ltp();
        this.ltq = snapshot.ltq();
        this.cumulativeVolume = snapshot.cumulativeVolume();
        this.dayVwap = snapshot.dayVwap();

        int incomingLevels = snapshot.depthLevels();
        if (incomingLevels != this.depthLevels) {
            this.depthLevels = incomingLevels;
            this.bidPrices = new double[incomingLevels];
            this.bidQuantities = new long[incomingLevels];
            this.bidOrders = new int[incomingLevels];
            this.askPrices = new double[incomingLevels];
            this.askQuantities = new long[incomingLevels];
            this.askOrders = new int[incomingLevels];
        }

        // Fast in-place primitive array copy (zero allocations)
        double[] inBids = snapshot.bidPrices();
        long[] inBidQtys = snapshot.bidQuantities();
        int[] inBidOrders = snapshot.bidOrders();
        double[] inAsks = snapshot.askPrices();
        long[] inAskQtys = snapshot.askQuantities();
        int[] inAskOrders = snapshot.askOrders();

        System.arraycopy(inBids, 0, this.bidPrices, 0, Math.min(inBids.length, depthLevels));
        System.arraycopy(inBidQtys, 0, this.bidQuantities, 0, Math.min(inBidQtys.length, depthLevels));
        System.arraycopy(inBidOrders, 0, this.bidOrders, 0, Math.min(inBidOrders.length, depthLevels));
        System.arraycopy(inAsks, 0, this.askPrices, 0, Math.min(inAsks.length, depthLevels));
        System.arraycopy(inAskQtys, 0, this.askQuantities, 0, Math.min(inAskQtys.length, depthLevels));
        System.arraycopy(inAskOrders, 0, this.askOrders, 0, Math.min(inAskOrders.length, depthLevels));

        // Evaluate State and track Crossed duration
        BookStateTag previousTag = this.stateTag;
        this.stateTag = CanonicalMarketSnapshot.evaluateStateTag(this.bidPrices, this.askPrices);

        if (this.stateTag == BookStateTag.STATE_CROSSED) {
            if (previousTag != BookStateTag.STATE_CROSSED) {
                this.crossedStateStartNanos = this.lastArrivalNanos;
            } else {
                this.totalCrossedDurationNanos += (this.lastArrivalNanos - this.crossedStateStartNanos);
                this.crossedStateStartNanos = this.lastArrivalNanos;
            }
        } else {
            this.crossedStateStartNanos = 0L;
        }

        this.updateCount++;
    }

    public double getMidPrice() {
        if (bidPrices.length > 0 && askPrices.length > 0 && bidPrices[0] > 0 && askPrices[0] > 0) {
            return (bidPrices[0] + askPrices[0]) / 2.0;
        }
        return ltp;
    }

    public double getSpread() {
        if (bidPrices.length > 0 && askPrices.length > 0 && bidPrices[0] > 0 && askPrices[0] > 0) {
            return askPrices[0] - bidPrices[0];
        }
        return 0.0;
    }

    /**
     * Creates an immutable point-in-time snapshot safely cloned for external readers.
     */
    public CanonicalMarketSnapshot toImmutableSnapshot() {
        return new CanonicalMarketSnapshot(
                sourceId != null ? sourceId : "UNKNOWN",
                instrumentToken,
                symbol != null ? symbol : "TOKEN_" + instrumentToken,
                lastArrivalNanos,
                lastArrivalMicros,
                exchangeTimestamp,
                ltp,
                ltq,
                cumulativeVolume,
                dayVwap,
                depthLevels,
                bidPrices,
                bidQuantities,
                bidOrders,
                askPrices,
                askQuantities,
                askOrders,
                stateTag
        );
    }

    // Getters
    public long getInstrumentToken() { return instrumentToken; }
    public String getSymbol() { return symbol; }
    public String getSourceId() { return sourceId; }
    public int getDepthLevels() { return depthLevels; }
    public long getLastArrivalNanos() { return lastArrivalNanos; }
    public long getLastArrivalMicros() { return lastArrivalMicros; }
    public long getExchangeTimestamp() { return exchangeTimestamp; }
    public double getLtp() { return ltp; }
    public long getLtq() { return ltq; }
    public long getCumulativeVolume() { return cumulativeVolume; }
    public double getDayVwap() { return dayVwap; }
    public double[] getBidPrices() { return bidPrices; }
    public long[] getBidQuantities() { return bidQuantities; }
    public int[] getBidOrders() { return bidOrders; }
    public double[] getAskPrices() { return askPrices; }
    public long[] getAskQuantities() { return askQuantities; }
    public int[] getAskOrders() { return askOrders; }
    public BookStateTag getStateTag() { return stateTag; }
    public long getTotalCrossedDurationNanos() { return totalCrossedDurationNanos; }
    public long getUpdateCount() { return updateCount; }
}
