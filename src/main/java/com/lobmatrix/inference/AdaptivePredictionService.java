package com.lobmatrix.inference;

import com.lobmatrix.parquet.ParquetFeatureRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;

/**
 * M5P1S3: Cold-start-safe prediction service.
 *
 * <p>Attempts to load the trained ONNX classifier via
 * {@link TickInferenceEvaluator}. If the model, its metadata, or its scaler
 * are missing or invalid (e.g. Day 1 before any training run has produced
 * champion_model.onnx), initialization does not throw: it logs a clear
 * warning and falls back to {@link InferenceMode#MODE_BASELINE_ACTIVE},
 * using {@link BaselineStrengthScoreCalculator} for every subsequent
 * prediction until the process is restarted with valid artifacts present.
 */
public final class AdaptivePredictionService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AdaptivePredictionService.class);

    private final InferenceMode mode;
    private final TickInferenceEvaluator evaluator;

    private AdaptivePredictionService(InferenceMode mode, TickInferenceEvaluator evaluator) {
        this.mode = mode;
        this.evaluator = evaluator;
    }

    /** Initializes against the default local M4P3 artifact paths. */
    public static AdaptivePredictionService initialize() {
        return initialize(
                Path.of(OnnxModelService.DEFAULT_MODEL_PATH),
                Path.of(OnnxModelService.DEFAULT_METADATA_PATH),
                Path.of(ScalerParams.DEFAULT_SCALER_PATH)
        );
    }

    /**
     * Initializes against explicit artifact paths. Never throws due to
     * missing/invalid ONNX artifacts; falls back to MODE_BASELINE_ACTIVE
     * instead.
     */
    public static AdaptivePredictionService initialize(
            Path modelPath, Path metadataPath, Path scalerPath
    ) {
        Objects.requireNonNull(modelPath, "modelPath");
        Objects.requireNonNull(metadataPath, "metadataPath");
        Objects.requireNonNull(scalerPath, "scalerPath");

        try {
            TickInferenceEvaluator evaluator = TickInferenceEvaluator.load(
                    modelPath, metadataPath, scalerPath
            );
            log.info(
                    "MODE_AI_PREDICTIVE_ACTIVE: loaded and verified {} against {} and {}.",
                    modelPath, metadataPath, scalerPath
            );
            return new AdaptivePredictionService(InferenceMode.MODE_AI_PREDICTIVE_ACTIVE, evaluator);
        } catch (RuntimeException exception) {
            log.warn(
                    "MODE_BASELINE_ACTIVE: ONNX artifacts unavailable or invalid ({}: {}). "
                            + "Falling back to deterministic Baseline Strength Score. "
                            + "Train and export a model to activate MODE_AI_PREDICTIVE_ACTIVE.",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return new AdaptivePredictionService(InferenceMode.MODE_BASELINE_ACTIVE, null);
        }
    }

    public InferenceMode mode() {
        return mode;
    }

    public PredictionResult evaluate(ParquetFeatureRecord record) {
        Objects.requireNonNull(record, "record");

        if (mode == InferenceMode.MODE_AI_PREDICTIVE_ACTIVE) {
            OnnxModelService.Prediction prediction = evaluator.evaluate(record);
            return new PredictionResult(
                    mode,
                    prediction.predictedOriginalLabel(),
                    Math.max(-1.0, Math.min(1.0, prediction.bullishScore())),
                    prediction.probabilityDown(),
                    prediction.probabilityNeutral(),
                    prediction.probabilityUp()
            );
        }

        return BaselineStrengthScoreCalculator.evaluate(record);
    }

    @Override
    public void close() {
        if (evaluator != null) {
            evaluator.close();
        }
    }
}
