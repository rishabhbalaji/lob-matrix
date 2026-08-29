package com.lobmatrix.engine.resample;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FixedTimeResamplingEngineTest {

    private CanonicalMarketSnapshot makeSnapshot(long arrivalNanos, double price) {
        return new CanonicalMarketSnapshot(
                "MOCK", 738561L, "RELIANCE",
                arrivalNanos, arrivalNanos / 1000L, arrivalNanos / 1_000_000_000L,
                price, 10, 1000, price, 1,
                new double[]{price - 0.50}, new long[]{100}, new int[]{1},
                new double[]{price + 0.50}, new long[]{100}, new int[]{1},
                BookStateTag.NORMAL
        );
    }

    @Test
    @DisplayName("Verify Zero-Lookahead LOCF Resampling on 1-second grid")
    void testZeroLookaheadLOCF() {
        // 1-second grid (1000 ms)
        FixedTimeResamplingEngine engine = new FixedTimeResamplingEngine(1000L);

        // Tick 1 at t=0 ms (Price 2500.0) -> Anchors grid at T0 = 0 ms
        CanonicalMarketSnapshot tick1 = makeSnapshot(0L, 2500.0);
        List<ResampledGridPoint> res1 = engine.onTick(tick1);
        assertThat(res1).isEmpty();

        // Tick 2 at t=400 ms (Price 2501.0) -> Before T1 (1000 ms), no emission yet
        CanonicalMarketSnapshot tick2 = makeSnapshot(400_000_000L, 2501.0);
        List<ResampledGridPoint> res2 = engine.onTick(tick2);
        assertThat(res2).isEmpty();

        // Tick 3 at t=1200 ms (Price 2505.0) -> Crosses T1 (1000 ms)!
        // At T1=1000 ms, the latest observation was tick2 (Price 2501.0 at 400 ms)
        // Zero-Lookahead rule: tick3 (2505.0) MUST NOT be in T1!
        CanonicalMarketSnapshot tick3 = makeSnapshot(1_200_000_000L, 2505.0);
        List<ResampledGridPoint> res3 = engine.onTick(tick3);

        assertThat(res3).hasSize(1);
        ResampledGridPoint grid1 = res3.get(0);
        assertThat(grid1.gridNanos()).isEqualTo(1_000_000_000L);
        assertThat(grid1.snapshot().ltp()).isEqualTo(2501.0); // Exactly tick2!
        assertThat(grid1.snapshotAgeMs()).isCloseTo(600.0, within(0.001)); // 1000ms - 400ms = 600ms old

        // Advance to t=3500 ms with no new ticks (quiet market)
        // Should emit T2 (2000 ms) and T3 (3000 ms) carrying forward tick3 (Price 2505.0)
        List<ResampledGridPoint> res4 = engine.advanceTo(3_500_000_000L);
        assertThat(res4).hasSize(2);

        ResampledGridPoint grid2 = res4.get(0);
        assertThat(grid2.gridNanos()).isEqualTo(2_000_000_000L);
        assertThat(grid2.snapshot().ltp()).isEqualTo(2505.0); // Carried forward
        assertThat(grid2.snapshotAgeMs()).isCloseTo(800.0, within(0.001)); // 2000ms - 1200ms = 800ms

        ResampledGridPoint grid3 = res4.get(1);
        assertThat(grid3.gridNanos()).isEqualTo(3_000_000_000L);
        assertThat(grid3.snapshot().ltp()).isEqualTo(2505.0); // Carried forward
        assertThat(grid3.snapshotAgeMs()).isCloseTo(1800.0, within(0.001)); // 3000ms - 1200ms = 1800ms
    }
}
