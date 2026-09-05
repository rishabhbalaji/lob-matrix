package com.lobmatrix.websocket;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookSnapshotMessageDashboardFeaturesTest {

    @Test
    void serializesLiveDashboardFeaturePercentages() {
        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK",
                1001L,
                "SYM1001",
                10L,
                1_700_000_000_000_000L,
                1_700_000_000L,
                100.50,
                25L,
                1_000L,
                100.50,
                1,
                new double[] {100.0},
                new long[] {300L},
                new int[] {3},
                new double[] {101.0},
                new long[] {100L},
                new int[] {1},
                BookStateTag.NORMAL
        );

        OrderBookSnapshotMessage message = OrderBookSnapshotMessage.from(
                snapshot,
                new LiveDashboardFeatureService.LiveDashboardFeatures(50.0, -25.5)
        );

        assertThat(message.depthImbalancePercent()).isEqualTo(50.0);
        assertThat(message.tradeStrengthPercent()).isEqualTo(-25.5);
    }

    @Test
    void defaultFactoryRetainsBackwardCompatibleNeutralFeatures() {
        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK",
                1001L,
                "SYM1001",
                10L,
                1_700_000_000_000_000L,
                1_700_000_000L,
                100.50,
                25L,
                1_000L,
                100.50,
                1,
                new double[] {100.0},
                new long[] {100L},
                new int[] {1},
                new double[] {101.0},
                new long[] {100L},
                new int[] {1},
                BookStateTag.NORMAL
        );

        OrderBookSnapshotMessage message = OrderBookSnapshotMessage.from(snapshot);

        assertThat(message.depthImbalancePercent()).isZero();
        assertThat(message.tradeStrengthPercent()).isZero();
    }
}
