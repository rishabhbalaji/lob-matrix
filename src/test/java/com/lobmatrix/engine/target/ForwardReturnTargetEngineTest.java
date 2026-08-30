package com.lobmatrix.engine.target;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.resample.ResampledGridPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ForwardReturnTargetEngineTest {

    private ResampledGridPoint makePoint(long seq, long nanos, double midPrice) {
        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 738561L, "RELIANCE",
                nanos, nanos / 1000L, nanos / 1_000_000_000L,
                midPrice, 10, 1000, midPrice, 1,
                new double[]{midPrice - 0.25}, new long[]{100}, new int[]{1},
                new double[]{midPrice + 0.25}, new long[]{100}, new int[]{1},
                BookStateTag.NORMAL
        );
        return new ResampledGridPoint(seq, nanos, 1_000_000_000L, snapshot, 0L);
    }

    @Test
    @DisplayName("Verify Zero-Lookahead forward return calculations across 1s, 5s, 10s, 30s, 60s")
    void testForwardReturnTargets() {
        ForwardReturnTargetEngine engine = new ForwardReturnTargetEngine();

        // Feed 65 seconds of 1-second grid points (0s to 65s)
        // Mid price starts at 1000.0 and grows by 1.0 each second
        for (int sec = 0; sec <= 65; sec++) {
            long nanos = sec * 1_000_000_000L;
            double mid = 1000.0 + sec;
            engine.appendGridPoint(makePoint(sec, nanos, mid));
        }

        assertThat(engine.getHistorySize()).isEqualTo(66);

        // Compute forward targets for index 0 (t = 0s, mid = 1000.0)
        ForwardReturnTargets targets = engine.computeTargetsIfMatured(0);
        assertThat(targets).isNotNull();
        assertThat(targets.baseMidPrice()).isEqualTo(1000.0);

        // R_1s = ln(1001.0 / 1000.0)
        assertThat(targets.return1s()).isCloseTo(Math.log(1001.0 / 1000.0), within(0.00001));

        // R_5s = ln(1005.0 / 1000.0)
        assertThat(targets.return5s()).isCloseTo(Math.log(1005.0 / 1000.0), within(0.00001));

        // R_10s = ln(1010.0 / 1000.0)
        assertThat(targets.return10s()).isCloseTo(Math.log(1010.0 / 1000.0), within(0.00001));

        // R_30s = ln(1030.0 / 1000.0)
        assertThat(targets.return30s()).isCloseTo(Math.log(1030.0 / 1000.0), within(0.00001));

        // R_60s = ln(1060.0 / 1000.0)
        assertThat(targets.return60s()).isCloseTo(Math.log(1060.0 / 1000.0), within(0.00001));

        // Index 10 (t = 10s) needs t = 70s to mature; currently history is only up to 65s -> returns null
        assertThat(engine.computeTargetsIfMatured(10)).isNull();
    }
}
