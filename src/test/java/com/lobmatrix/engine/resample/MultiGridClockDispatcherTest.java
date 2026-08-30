package com.lobmatrix.engine.resample;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MultiGridClockDispatcherTest {

    private CanonicalMarketSnapshot makeSnapshot(long arrivalNanos, double price) {
        return new CanonicalMarketSnapshot(
                "MOCK", 738561L, "RELIANCE",
                arrivalNanos, arrivalNanos / 1000L, 1700000000L,
                price, 10, 1000, price, 1,
                new double[]{price - 0.50}, new long[]{100}, new int[]{1},
                new double[]{price + 0.50}, new long[]{100}, new int[]{1},
                BookStateTag.NORMAL
        );
    }

    @Test
    @DisplayName("Verify synchronized multi-grid dispatch across all 5 standard sampling frequencies")
    void testMultiGridSynchronization() {
        MultiGridClockDispatcher dispatcher = new MultiGridClockDispatcher();

        Map<Long, AtomicInteger> emissionCounts = new HashMap<>();
        for (long interval : MultiGridClockDispatcher.STANDARD_INTERVALS_MS) {
            emissionCounts.put(interval, new AtomicInteger(0));
        }

        dispatcher.registerListener((interval, pt) -> {
            emissionCounts.get(interval).incrementAndGet();
            assertThat(pt.snapshot().symbol()).isEqualTo("RELIANCE");
        });

        // Tick 1 at t = 0 ms -> Anchors all 5 grids
        dispatcher.onTick(makeSnapshot(0L, 2500.0));

        // Advance time to t = 1000 ms (1.0 second elapsed)
        dispatcher.advanceTo(1_000_000_000L);

        // Verification of frequencies over 1,000 ms:
        // 100ms grid  -> 10 steps (100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
        assertThat(emissionCounts.get(100L).get()).isEqualTo(10);

        // 250ms grid  -> 4 steps (250, 500, 750, 1000)
        assertThat(emissionCounts.get(250L).get()).isEqualTo(4);

        // 500ms grid  -> 2 steps (500, 1000)
        assertThat(emissionCounts.get(500L).get()).isEqualTo(2);

        // 1000ms grid -> 1 step (1000)
        assertThat(emissionCounts.get(1000L).get()).isEqualTo(1);

        // 2000ms grid -> 0 steps (needs 2000ms to emit first point)
        assertThat(emissionCounts.get(2000L).get()).isEqualTo(0);

        // Advance to t = 2000 ms
        dispatcher.advanceTo(2_000_000_000L);

        // Now 2000ms grid emits its first point!
        assertThat(emissionCounts.get(2000L).get()).isEqualTo(1);
    }
}
