package com.lobmatrix.inference;

/**
 * M5P1S3 runtime status flag reported at startup and on every prediction.
 *
 * <p>MODE_AI_PREDICTIVE_ACTIVE: the trained ONNX classifier loaded and
 * verified successfully; predictions come from the model.
 *
 * <p>MODE_BASELINE_ACTIVE: the ONNX model, its metadata, or its scaler were
 * missing/invalid at startup (e.g. cold start on Day 1 before any training
 * run has produced champion_model.onnx); predictions fall back to a
 * deterministic, non-ML Baseline Strength Score so the engine never crashes
 * or blocks on a missing model artifact.
 */
public enum InferenceMode {
    MODE_AI_PREDICTIVE_ACTIVE,
    MODE_BASELINE_ACTIVE
}
