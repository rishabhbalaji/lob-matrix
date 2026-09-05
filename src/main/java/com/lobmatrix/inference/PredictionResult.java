package com.lobmatrix.inference;

/**
 * Unified prediction result across both {@link InferenceMode}s.
 *
 * <p>predictedLabel is always one of {-1, 0, +1} in both modes.
 * bullishScore is always in [-1, 1] in both modes, so downstream consumers
 * (dashboard gauges, logs) can treat AI and baseline output uniformly.
 *
 * <p>probabilityDown/Neutral/Up are only populated in
 * MODE_AI_PREDICTIVE_ACTIVE; they are {@code null} in MODE_BASELINE_ACTIVE
 * because the deterministic formula does not produce calibrated
 * probabilities.
 */
public record PredictionResult(
        InferenceMode mode,
        int predictedLabel,
        double bullishScore,
        Float probabilityDown,
        Float probabilityNeutral,
        Float probabilityUp
) {
    public PredictionResult {
        if (predictedLabel < -1 || predictedLabel > 1) {
            throw new IllegalArgumentException("predictedLabel must be one of -1, 0, 1");
        }
        if (!Double.isFinite(bullishScore) || bullishScore < -1.0 || bullishScore > 1.0) {
            throw new IllegalArgumentException("bullishScore must be finite and within [-1, 1]");
        }
        if (mode == InferenceMode.MODE_AI_PREDICTIVE_ACTIVE) {
            if (probabilityDown == null || probabilityNeutral == null || probabilityUp == null) {
                throw new IllegalArgumentException(
                        "MODE_AI_PREDICTIVE_ACTIVE requires all three probabilities"
                );
            }
        } else if (probabilityDown != null || probabilityNeutral != null || probabilityUp != null) {
            throw new IllegalArgumentException(
                    "MODE_BASELINE_ACTIVE must not report calibrated probabilities"
            );
        }
    }
}
