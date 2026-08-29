package com.lobmatrix.core.adapter;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class MarketFeedAdapterTest {

    // Test implementation of the SPI to verify contract guarantees
    static class DummyTestAdapter implements MarketFeedAdapter {
        private FeedStatus status = FeedStatus.DISCONNECTED;
        private final Set<Long> subscribedTokens = new HashSet<>();
        private final List<Consumer<CanonicalMarketSnapshot>> listeners = new CopyOnWriteArrayList<>();

        @Override public String getSourceId() { return "TEST_ADAPTER"; }
        @Override public FeedStatus getStatus() { return status; }
        @Override public void connect() { this.status = FeedStatus.CONNECTED; }
        @Override public void disconnect() { this.status = FeedStatus.DISCONNECTED; }
        @Override public void subscribe(Set<Long> tokens) { subscribedTokens.addAll(tokens); }
        @Override public void unsubscribe(Set<Long> tokens) { subscribedTokens.removeAll(tokens); }
        @Override public void registerListener(Consumer<CanonicalMarketSnapshot> listener) { listeners.add(listener); }
        @Override public void unregisterListener(Consumer<CanonicalMarketSnapshot> listener) { listeners.remove(listener); }

        public void emit(CanonicalMarketSnapshot snapshot) {
            for (Consumer<CanonicalMarketSnapshot> l : listeners) {
                l.accept(snapshot);
            }
        }
    }

    @Test
    @DisplayName("Verify MarketFeedAdapter lifecycle, listener dispatch, and subscription management")
    void testAdapterContract() {
        DummyTestAdapter adapter = new DummyTestAdapter();
        assertThat(adapter.getStatus()).isEqualTo(FeedStatus.DISCONNECTED);

        adapter.connect();
        assertThat(adapter.getStatus()).isEqualTo(FeedStatus.CONNECTED);

        adapter.subscribe(Set.of(738561L, 256265L));
        assertThat(adapter.subscribedTokens).containsExactlyInAnyOrder(738561L, 256265L);

        AtomicInteger receivedCount = new AtomicInteger(0);
        Consumer<CanonicalMarketSnapshot> listener = snapshot -> {
            assertThat(snapshot.symbol()).isEqualTo("RELIANCE");
            receivedCount.incrementAndGet();
        };

        adapter.registerListener(listener);

        // Emit a sample canonical frame
        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "TEST_ADAPTER", 738561L, "RELIANCE",
                1000L, 2000L, 3000L,
                2500.0, 10, 1000, 2500.0, 1,
                new double[]{2499.0}, new long[]{100}, new int[]{1},
                new double[]{2501.0}, new long[]{100}, new int[]{1},
                BookStateTag.NORMAL
        );

        adapter.emit(snapshot);
        assertThat(receivedCount.get()).isEqualTo(1);

        // Unregister listener and emit again
        adapter.unregisterListener(listener);
        adapter.emit(snapshot);
        assertThat(receivedCount.get()).isEqualTo(1); // Should not increase

        adapter.disconnect();
        assertThat(adapter.getStatus()).isEqualTo(FeedStatus.DISCONNECTED);
    }
}
