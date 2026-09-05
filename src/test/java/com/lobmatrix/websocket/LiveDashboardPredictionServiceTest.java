package com.lobmatrix.websocket;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.inference.AdaptivePredictionService;
import com.lobmatrix.inference.InferenceMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LiveDashboardPredictionServiceTest {

    @Test
    void fallsBackToClearlyLabelledBoundedDisplayForecastWhenModelIsAbsent(
            @TempDir Path tempDir
    ) {
        try (AdaptivePredictionService adaptivePredictionService =
                     AdaptivePredictionService.initialize(
                             tempDir.resolve("champion_model.onnx"),
                             tempDir.resolve("modelmetadata.json"),
                             tempDir.resolve("scalerparams.json")
                     );
             LiveDashboardPredictionService service =
                     new LiveDashboardPredictionService(adaptivePredictionService)) {

            LiveDashboardPredictionService.LiveDashboardForecast forecast =
                    service.calculate(snapshot(1001L, 101.0, 200L, 100L));

            assertThat(forecast.predictionMode())
                    .isEqualTo(InferenceMode.MODE_BASELINE_ACTIVE.name());
            assertThat(forecast.calibratedProbabilities()).isFalse();
            assertThat(forecast.probabilityDownPercent()).isBetween(0.0, 100.0);
            assertThat(forecast.probabilityNeutralPercent()).isBetween(0.0, 100.0);
            assertThat(forecast.probabilityUpPercent()).isBetween(0.0, 100.0);
            assertThat(
                    forecast.probabilityDownPercent()
                            + forecast.probabilityNeutralPercent()
                            + forecast.probabilityUpPercent()
            ).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.0001));
        }
    }

    @Test
    void retainsOnlyOnePredictionStatePerInstrument() {
        try (LiveDashboardPredictionService service = new LiveDashboardPredictionService()) {
            service.calculate(snapshot(1001L, 101.0, 100L, 100L));
            service.calculate(snapshot(1001L, 101.1, 120L, 80L));
            service.calculate(snapshot(2002L, 101.0, 100L, 100L));

            assertThat(service.trackedInstrumentCount()).isEqualTo(2);
        }
    }

    @Test
    void nullSnapshotReturnsNeutralBaselineForecast() {
        try (LiveDashboardPredictionService service = new LiveDashboardPredictionService()) {
            LiveDashboardPredictionService.LiveDashboardForecast forecast =
                    service.calculate(null);

            assertThat(forecast.predictionMode())
                    .isEqualTo(InferenceMode.MODE_BASELINE_ACTIVE.name());
            assertThat(forecast.probabilityDownPercent()).isZero();
            assertThat(forecast.probabilityNeutralPercent()).isEqualTo(100.0);
            assertThat(forecast.probabilityUpPercent()).isZero();
            assertThat(forecast.calibratedProbabilities()).isFalse();
        }
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
                1_000_000L,
                1_700_000_000_000_000L,
                1_700_000_000L,
                lastPrice,
                50L,
                10_000L,
                100.50,
                5,
                new double[] {100.0, 99.9, 99.8, 99.7, 99.6},
                new long[] {bidQuantity, bidQuantity, bidQuantity, bidQuantity, bidQuantity},
                new int[] {1, 1, 1, 1, 1},
                new double[] {101.0, 101.1, 101.2, 101.3, 101.4},
                new long[] {askQuantity, askQuantity, askQuantity, askQuantity, askQuantity},
                new int[] {1, 1, 1, 1, 1},
                BookStateTag.NORMAL
        );
    }
}
