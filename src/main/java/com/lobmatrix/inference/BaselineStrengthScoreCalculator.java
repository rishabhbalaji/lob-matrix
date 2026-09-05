package com.lobmatrix.inference;

import com.lobmatrix.parquet.ParquetFeatureRecord;

/**
 * Deterministic, non-ML fallback formula used only when the trained ONNX
 * model is unavailable (MODE_BASELINE_ACTIVE).
 *
 * <p>Based on the original design's composite "Strength Score"
 * (Order-Book-analyses-2.docx, section 25):
 * {@code score = w1*WeightedImbalance + w2*TradeStrength + w3*MicropricePressure}.
 *
 * <p>Per that same document: "The weights should not be arbitrarily chosen
 * in production." These fixed weights are a deliberately simple safety-net
 * heuristic for cold start only. They are NOT calibrated on historical data
 * and must never be treated as a validated trading signal; MODE_BASELINE_ACTIVE
 * exists purely so the engine starts cleanly instead of crashing when
 * model.onnx is missing.
 */
final class BaselineStrengthScoreCalculator {

    private static final double WEIGHT_OBI = 0.35;
    private static final double WEIGHT_TRADE_STRENGTH = 0.35;
    private static final double WEIGHT_MICROPRICE_PRESSURE = 0.30;
    private static final double MICROPRICE_PRESSURE_BPS_SCALE = 50.0;
    private static final double NEUTRAL_BAND = 0.15;

    private BaselineStrengthScoreCalculator() {
    }

    static PredictionResult evaluate(ParquetFeatureRecord record) {
        double obiComponent = clampToUnitInterval(record.weightedOBILinear());
        double tradeComponent = clampToUnitInterval(record.tradeStrength());
        double pressureComponent = Math.tanh(
                safeFinite(record.micropricePressureBps()) / MICROPRICE_PRESSURE_BPS_SCALE
        );

        double rawScore = WEIGHT_OBI * obiComponent
                + WEIGHT_TRADE_STRENGTH * tradeComponent
                + WEIGHT_MICROPRICE_PRESSURE * pressureComponent;

        double score = Math.max(-1.0, Math.min(1.0, rawScore));

        int label;
        if (score > NEUTRAL_BAND) {
            label = 1;
        } else if (score < -NEUTRAL_BAND) {
            label = -1;
        } else {
            label = 0;
        }

        return new PredictionResult(
                InferenceMode.MODE_BASELINE_ACTIVE,
                label,
                score,
                null,
                null,
                null
        );
    }

    private static double clampToUnitInterval(double value) {
        double finite = safeFinite(value);
        return Math.max(-1.0, Math.min(1.0, finite));
    }

    private static double safeFinite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
