package com.lobmatrix.engine.target;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VolatilityThresholdClassifierTest {

    @Test
    @DisplayName("Verify horizon-scaled volatility threshold scales with sqrt(tau / delta_t)")
    void testVolatilityScaling() {
        double sigma1s = 0.0004; // 4 bps 1-second rolling volatility
        long delta1s = 1_000_000_000L;
        long tau4s = 4_000_000_000L; // 4-second horizon (ratio = 4, sqrt = 2)

        double spread = 0.10;
        double midPrice = 1000.0; // half relative spread = 0.10 / 2000 = 0.00005 (0.5 bps)

        // 0.5 * (0.0004 * sqrt(4)) = 0.5 * (0.0008) = 0.00040 (4 bps)
        // max(4 bps, 0.5 bps) = 4 bps = 0.00040
        double threshold = VolatilityThresholdClassifier.computeDefaultThreshold(sigma1s, delta1s, tau4s, spread, midPrice);
        assertThat(threshold).isCloseTo(0.00040, within(0.000001));

        // When spread is extremely wide (e.g. spread = 2.0 on 1000.0 -> half spread = 1.0/1000 = 10 bps)
        // Threshold must clamp to half relative spread (10 bps > 4 bps)
        double wideSpreadThreshold = VolatilityThresholdClassifier.computeDefaultThreshold(sigma1s, delta1s, tau4s, 2.0, midPrice);
        assertThat(wideSpreadThreshold).isCloseTo(0.00100, within(0.000001));
    }

    @Test
    @DisplayName("Verify tri-class directional classification {-1, 0, +1}")
    void testDirectionalClassification() {
        double threshold = 0.0005; // 5 bps

        // Return = +8 bps -> UP
        assertThat(VolatilityThresholdClassifier.classifyDirection(0.0008, threshold))
                .isEqualTo(DirectionalLabel.UP);
        assertThat(DirectionalLabel.UP.getValue()).isEqualTo(1);

        // Return = -7 bps -> DOWN
        assertThat(VolatilityThresholdClassifier.classifyDirection(-0.0007, threshold))
                .isEqualTo(DirectionalLabel.DOWN);
        assertThat(DirectionalLabel.DOWN.getValue()).isEqualTo(-1);

        // Return = +2 bps (inside [-5 bps, +5 bps]) -> NEUTRAL
        assertThat(VolatilityThresholdClassifier.classifyDirection(0.0002, threshold))
                .isEqualTo(DirectionalLabel.NEUTRAL);
        assertThat(DirectionalLabel.NEUTRAL.getValue()).isEqualTo(0);
    }
}
