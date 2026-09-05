package com.lobmatrix.inference;

import com.lobmatrix.parquet.ParquetFeatureRecord;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * M5P1S2: Real-time tick inference evaluator.
 *
 * <p>Converts a live {@link ParquetFeatureRecord} into the model's exact
 * 12-feature order, applies the training-only standard scaler, and runs
 * cached ONNX Runtime inference without reloading the model per tick.
 *
 * <p>Feature order is fixed and cross-verified at startup against both the
 * scaler contract and the ONNX model metadata, so any future drift in
 * feature ordering fails closed at construction rather than silently
 * mis-aligning live predictions.
 */
public final class TickInferenceEvaluator implements AutoCloseable {

    private static final List<String> EXPECTED_FEATURE_ORDER = List.of(
            "l1_obi", "total_obi", "w_obi_lin", "w_obi_exp",
            "microprice", "micro_pressure", "l1_ofi",
            "ml_ofi_uniform", "ml_ofi_exp", "trade_strength",
            "rel_spread_bps", "snapshot_age_ms"
    );

    private final OnnxModelService modelService;
    private final ScalerParams scalerParams;

    private TickInferenceEvaluator(OnnxModelService modelService, ScalerParams scalerParams) {
        this.modelService = modelService;
        this.scalerParams = scalerParams;
    }

    public static TickInferenceEvaluator loadDefault() {
        return load(
                Path.of(OnnxModelService.DEFAULT_MODEL_PATH),
                Path.of(OnnxModelService.DEFAULT_METADATA_PATH),
                Path.of(ScalerParams.DEFAULT_SCALER_PATH)
        );
    }

    public static TickInferenceEvaluator load(Path modelPath, Path metadataPath, Path scalerPath) {
        OnnxModelService modelService = OnnxModelService.load(modelPath, metadataPath);
        ScalerParams scalerParams;
        try {
            scalerParams = ScalerParams.load(scalerPath, metadataPath);
        } catch (RuntimeException exception) {
            modelService.close();
            throw exception;
        }

        if (!EXPECTED_FEATURE_ORDER.equals(modelService.featureOrder())) {
            modelService.close();
            throw new IllegalStateException(
                    "ONNX model feature order " + modelService.featureOrder()
                            + " does not match evaluator's expected order " + EXPECTED_FEATURE_ORDER
            );
        }
        if (!EXPECTED_FEATURE_ORDER.equals(scalerParams.featureOrder())) {
            modelService.close();
            throw new IllegalStateException(
                    "Scaler feature order " + scalerParams.featureOrder()
                            + " does not match evaluator's expected order " + EXPECTED_FEATURE_ORDER
            );
        }

        return new TickInferenceEvaluator(modelService, scalerParams);
    }

    /**
     * Extracts, scales, and predicts on one live feature record. Reuses the
     * already-loaded ONNX session; does not reload the model per call.
     */
    public OnnxModelService.Prediction evaluate(ParquetFeatureRecord record) {
        Objects.requireNonNull(record, "record");

        double[] raw = {
                record.level1OBI(),
                record.totalOBI(),
                record.weightedOBILinear(),
                record.weightedOBIExp(),
                record.level1Microprice(),
                record.micropricePressure(),
                record.level1OFI(),
                record.multiLevelOFIUniform(),
                record.multiLevelOFIExp(),
                record.tradeStrength(),
                record.relativeSpreadBps(),
                record.snapshotAgeMs()
        };

        double[] scaled = scalerParams.transform(raw);
        float[] scaledFloat = new float[scaled.length];
        for (int i = 0; i < scaled.length; i++) {
            scaledFloat[i] = (float) scaled[i];
        }

        return modelService.predict(scaledFloat);
    }

    public List<String> featureOrder() {
        return EXPECTED_FEATURE_ORDER;
    }

    @Override
    public void close() {
        modelService.close();
    }
}
