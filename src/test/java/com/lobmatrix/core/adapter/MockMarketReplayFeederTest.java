package com.lobmatrix.core.adapter;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MockMarketReplayFeederTest {

    @Test
    @DisplayName("Verify MockMarketReplayFeeder deterministic generation and depth arrays")
    void testDeterministicGeneration() {
        MockMarketReplayFeeder feeder = new MockMarketReplayFeeder("MOCK", 5, 10, 12345L);
        CanonicalMarketSnapshot snap1 = feeder.generateNextSnapshot(738561L, "RELIANCE");

        assertThat(snap1.sourceId()).isEqualTo("MOCK");
        assertThat(snap1.symbol()).isEqualTo("RELIANCE");
        assertThat(snap1.depthLevels()).isEqualTo(5);
        assertThat(snap1.bidPrices().length).isEqualTo(5);
        assertThat(snap1.askPrices().length).isEqualTo(5);
        assertThat(snap1.stateTag()).isEqualTo(BookStateTag.NORMAL);
        assertThat(snap1.bidPrices()[0]).isLessThan(snap1.askPrices()[0]); // Valid spread
    }

    @Test
    @DisplayName("Verify MockMarketReplayFeeder real-time async emission to registered listeners")
    void testAsyncStreamEmission() throws InterruptedException {
        // Fast feeder: emits every 2 ms
        MockMarketReplayFeeder feeder = new MockMarketReplayFeeder("MOCK", 5, 2, 999L);
        feeder.subscribe(Set.of(1001L, 1002L));

        CountDownLatch latch = new CountDownLatch(20);
        AtomicInteger totalReceived = new AtomicInteger(0);

        feeder.registerListener(snapshot -> {
            totalReceived.incrementAndGet();
            latch.countDown();
        });

        feeder.connect();
        assertThat(feeder.getStatus()).isEqualTo(FeedStatus.CONNECTED);

        // Wait for at least 20 ticks to arrive
        boolean received = latch.await(2, TimeUnit.SECONDS);
        feeder.disconnect();

        assertThat(received).isTrue();
        assertThat(totalReceived.get()).isGreaterThanOrEqualTo(20);
        assertThat(feeder.getStatus()).isEqualTo(FeedStatus.DISCONNECTED);
    }
}
