package com.lobmatrix.engine.state;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-confined manager maintaining active live order book states across all subscribed instruments.
 */
public class OrderBookStateManager {

    private static final Logger log = LoggerFactory.getLogger(OrderBookStateManager.class);
    private final Map<Long, MutableOrderBookState> books = new ConcurrentHashMap<>();

    /**
     * Updates or initializes the state of an instrument from an incoming market snapshot.
     */
    public MutableOrderBookState applySnapshot(CanonicalMarketSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        MutableOrderBookState state = books.computeIfAbsent(
                snapshot.instrumentToken(),
                token -> new MutableOrderBookState(token, snapshot.depthLevels())
        );

        state.update(snapshot);
        return state;
    }

    /**
     * Retrieves the live mutable state for an instrument token.
     */
    public MutableOrderBookState getState(long instrumentToken) {
        return books.get(instrumentToken);
    }

    /**
     * Generates an immutable point-in-time snapshot for safe dispatch to external threads.
     */
    public CanonicalMarketSnapshot getImmutableSnapshot(long instrumentToken) {
        MutableOrderBookState state = books.get(instrumentToken);
        return state != null ? state.toImmutableSnapshot() : null;
    }

    /**
     * Returns the total count of active instruments being tracked.
     */
    public int getActiveInstrumentCount() {
        return books.size();
    }

    /**
     * Clears all order book states (used during session reset / daily market close).
     */
    public void reset() {
        books.clear();
        log.info("OrderBookStateManager reset cleanly.");
    }
}
