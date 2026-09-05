package com.lobmatrix.websocket;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.inference.InferenceMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookSnapshotMessageForecastTest {

    @Test
    void serializesLiveFiveSecondForecastFields() {
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

        LiveDashboardFeatureService.LiveDashboardFeatures features =
                new LiveDashboardFeatureService.LiveDashboardFeatures(50.0, 25.0);
        LiveDashboardPredictionService.LiveDashboardForecast forecast =
                new LiveDashboardPredictionService.LiveDashboardForecast(
                        InferenceMode.MODE_AI_PREDICTIVE_ACTIVE.name(),
                        12.5,
                        22.5,
                        65.0,
                        52.5,
                        true
                );

        OrderBookSnapshotMessage message =
                OrderBookSnapshotMessage.from(snapshot, features, forecast);

        assertThat(message.predictionMode())
                .isEqualTo(InferenceMode.MODE_AI_PREDICTIVE_ACTIVE.name());
        assertThat(message.probabilityDownPercent()).isEqualTo(12.5);
        assertThat(message.probabilityNeutralPercent()).isEqualTo(22.5);
        assertThat(message.probabilityUpPercent()).isEqualTo(65.0);
        assertThat(message.predictionScorePercent()).isEqualTo(52.5);
        assertThat(message.calibratedProbabilities()).isTrue();
    }
}
