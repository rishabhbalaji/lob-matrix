package com.lobmatrix.websocket;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveDashboardFeatureServiceTest {

    @Test
    void calculatesDepthImbalanceAsDisplaySafePercent() {
        LiveDashboardFeatureService service = new LiveDashboardFeatureService();

        LiveDashboardFeatureService.LiveDashboardFeatures features =
                service.calculate(snapshot(1001L, 101.0, 300L, 100L));

        assertThat(features.depthImbalancePercent()).isEqualTo(50.0);
        assertThat(features.tradeStrengthPercent()).isBetween(-100.0, 100.0);
    }

    @Test
    void tracksTradeStrengthIndependentlyPerInstrument() {
        LiveDashboardFeatureService service = new LiveDashboardFeatureService();

        service.calculate(snapshot(1001L, 101.0, 100L, 100L));
        service.calculate(snapshot(2002L, 100.0, 100L, 100L));

        assertThat(service.trackedInstrumentCount()).isEqualTo(2);
    }

    @Test
    void returnsNeutralValuesForNullSnapshot() {
        LiveDashboardFeatureService service = new LiveDashboardFeatureService();

        LiveDashboardFeatureService.LiveDashboardFeatures features = service.calculate(null);

        assertThat(features.depthImbalancePercent()).isZero();
        assertThat(features.tradeStrengthPercent()).isZero();
    }

    private static CanonicalMarketSnapshot snapshot(
            long token,
            double lastPrice,
            long bidQuantity,
            long askQuantity
    ) {
        return new CanonicalMarketSnapshot(
                "MOCK",
                token,
                "SYM" + token,
                10L,
                1_700_000_000_000_000L,
                1_700_000_000L,
                lastPrice,
                100L,
                1_000L,
                100.50,
                1,
                new double[] {100.0},
                new long[] {bidQuantity},
                new int[] {1},
                new double[] {101.0},
                new long[] {askQuantity},
                new int[] {1},
                BookStateTag.NORMAL
        );
    }
}
