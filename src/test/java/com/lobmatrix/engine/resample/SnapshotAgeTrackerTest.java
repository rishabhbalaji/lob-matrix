package com.lobmatrix.engine.resample;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SnapshotAgeTrackerTest {

    private CanonicalMarketSnapshot makeSnapshot(long arrivalNanos) {
        return new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST",
                arrivalNanos, arrivalNanos / 1000L, 1700000000L,
                100.0, 10, 1000, 100.0, 1,
                new double[]{99.5}, new long[]{100}, new int[]{1},
                new double[]{100.5}, new long[]{100}, new int[]{1},
                BookStateTag.NORMAL
        );
    }

    @Test
    @DisplayName("Verify empirical arrival gap metrics (min, max, average gap)")
    void testArrivalGapTracking() {
        SnapshotAgeTracker tracker = new SnapshotAgeTracker(5000L);

        // Tick 1 at t = 100ms
        tracker.recordTickArrival(makeSnapshot(100_000_000L));

        // Tick 2 at t = 120ms (gap = 20ms)
        tracker.recordTickArrival(makeSnapshot(120_000_000L));

        // Tick 3 at t = 200ms (gap = 80ms)
        tracker.recordTickArrival(makeSnapshot(200_000_000L));

        // Gap count = 2, total = 100ms, avg = 50ms, min = 20ms, max = 80ms
        assertThat(tracker.getGapCount()).isEqualTo(2L);
        assertThat(tracker.getAverageArrivalGapMs()).isCloseTo(50.0, within(0.001));
        assertThat(tracker.getMinArrivalGapMs()).isCloseTo(20.0, within(0.001));
        assertThat(tracker.getMaxArrivalGapMs()).isCloseTo(80.0, within(0.001));
    }

    @Test
    @DisplayName("Verify staleness filter marks observations older than 5000ms as stale")
    void testStalenessDetection() {
        SnapshotAgeTracker tracker = new SnapshotAgeTracker(5000L); // 5s threshold

        CanonicalMarketSnapshot snapshot = makeSnapshot(1_000_000_000L); // arrived at t=1s

        // Fresh Grid Point: sampled at t=1.5s (Age = 500ms)
        ResampledGridPoint freshPoint = new ResampledGridPoint(
                1L, 1_500_000_000L, 100_000_000L, snapshot, 500_000_000L
        );
        assertThat(tracker.calculateAgeMs(freshPoint)).isCloseTo(500.0, within(0.001));
        assertThat(tracker.isStale(freshPoint)).isFalse();

        // Stale Grid Point: sampled at t=7.0s (Age = 6000ms > 5000ms threshold)
        ResampledGridPoint stalePoint = new ResampledGridPoint(
                2L, 7_000_000_000L, 100_000_000L, snapshot, 6_000_000_000L
        );
        assertThat(tracker.calculateAgeMs(stalePoint)).isCloseTo(6000.0, within(0.001));
        assertThat(tracker.isStale(stalePoint)).isTrue();
        assertThat(tracker.getStaleDropCount()).isEqualTo(1L);
    }
}
