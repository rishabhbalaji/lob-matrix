package com.lobmatrix.core.adapter;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Service Provider Interface (SPI) for market data ingestion.
 * Decouples broker-specific networking/decoding from downstream feature calculation.
 */
public interface MarketFeedAdapter {

    /**
     * Unique identifier for the feed implementation (e.g. "ZERODHA", "DHAN", "MOCK").
     */
    String getSourceId();

    /**
     * Current connection lifecycle state.
     */
    FeedStatus getStatus();

    /**
     * Initiates the network connection to the market data stream.
     */
    void connect();

    /**
     * Gracefully disconnects and releases network/thread resources.
     */
    void disconnect();

    /**
     * Subscribes to a set of instrument tokens.
     *
     * @param tokens Set of instrument tokens to stream (e.g. 738561 for RELIANCE)
     */
    void subscribe(Set<Long> tokens);

    /**
     * Unsubscribes from specific instrument tokens.
     */
    void unsubscribe(Set<Long> tokens);

    /**
     * Registers a high-speed listener that receives decoded canonical snapshots.
     */
    void registerListener(Consumer<CanonicalMarketSnapshot> listener);

    /**
     * Removes a previously registered listener.
     */
    void unregisterListener(Consumer<CanonicalMarketSnapshot> listener);
}
