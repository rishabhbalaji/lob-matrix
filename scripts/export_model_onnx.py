"""
M4P3S1: Train, export, and verify a LightGBM histogram classifier in ONNX.

This environment's onnxmltools converter exports LightGBM multiclass
probabilities as ONNX-ML ZipMap output:

  input:         features      float32 [N, 12]
  output:        label         int64   [N], remapped labels {0,1,2}
  output:        probabilities sequence<map<int64, float>>, one map per row

Each probability map uses remapped class keys:
0 -> original -1 (down), 1 -> original 0 (neutral), 2 -> original +1 (up).

The ZipMap contract is valid ONNX / ONNX Runtime. The M4P3S2 metadata stage
must preserve this output format and class-key mapping for the Java consumer.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import numpy as np
import onnx
import onnxruntime as ort
import polars as pl
from onnxmltools import convert_lightgbm
from onnxmltools.convert.common.data_types import FloatTensorType

from duckdb_feature_loader import DuckDBFeatureLoader
from purged_cv_validator import generate_walk_forward_folds
from train_classifiers import train_lightgbm_hist_classifier


CANONICAL_FEATURES = [
    "l1_obi",
    "total_obi",
    "w_obi_lin",
    "w_obi_exp",
    "microprice",
    "micro_pressure",
    "l1_ofi",
    "ml_ofi_uniform",
    "ml_ofi_exp",
    "trade_strength",
    "rel_spread_bps",
    "snapshot_age_ms",
]
TARGET_HORIZON = "y_5s"
N_CLASSES = 3
PROBABILITY_TOLERANCE = 1e-5


def finite_rows(df: pl.DataFrame, columns: list[str]) -> pl.DataFrame:
    """Select columns and remove Polars nulls plus floating-point NaNs."""
    result = df.select(columns).drop_nulls()
    for column in columns:
        if result.schema[column] in (pl.Float32, pl.Float64):
            result = result.filter(pl.col(column).is_not_nan())
    return result


def patch_dynamic_label_output(model: onnx.ModelProto) -> None:
    """Correct converter label metadata from fixed [1] to dynamic [N]."""
    for output in model.graph.output:
        if output.name != "label":
            continue
        dims = output.type.tensor_type.shape.dim
        if len(dims) != 1:
            raise ValueError(f"Expected rank-1 label output; got rank {len(dims)}")
        dims[0].ClearField("dim_value")
        dims[0].dim_param = "N"
        return
    raise ValueError("Converted ONNX model has no 'label' output")


def output_contract(model: onnx.ModelProto, output_name: str) -> dict[str, Any]:
    """Serialize ONNX output type/shape in JSON-safe form."""
    for output in model.graph.output:
        if output.name != output_name:
            continue

        value_type = output.type
        if value_type.HasField("tensor_type"):
            dims = [
                dim.dim_param if dim.dim_param else dim.dim_value
                for dim in value_type.tensor_type.shape.dim
            ]
            return {
                "name": output_name,
                "onnx_type": "tensor",
                "shape": dims,
                "element_type": value_type.tensor_type.elem_type,
            }

        if value_type.HasField("sequence_type"):
            element = value_type.sequence_type.elem_type
            if element.HasField("map_type"):
                return {
                    "name": output_name,
                    "onnx_type": "sequence<map>",
                    "key_type": element.map_type.key_type,
                    "value_type": int(element.map_type.value_type.tensor_type.elem_type),
                    "per_row_keys": [0, 1, 2],
                }
            return {"name": output_name, "onnx_type": "sequence"}

        return {"name": output_name, "onnx_type": "unknown"}

    raise KeyError(f"Missing ONNX output: {output_name}")


def zipmap_to_probability_matrix(outputs: list[object]) -> np.ndarray:
    """
    Converts ONNX Runtime ZipMap output to dense [N, 3] probabilities for
    numerical equivalence testing. The exported ONNX artifact remains ZipMap.
    """
    maps = outputs[-1]
    if not isinstance(maps, list):
        raise AssertionError(
            "Expected ONNX Runtime ZipMap output as a list of dictionaries; "
            f"got {type(maps).__name__}"
        )

    matrix = np.empty((len(maps), N_CLASSES), dtype=np.float64)
    for row_index, class_probabilities in enumerate(maps):
        if not isinstance(class_probabilities, dict):
            raise AssertionError(
                f"ZipMap row {row_index} is {type(class_probabilities).__name__}, "
                "not a dictionary"
            )
        matrix[row_index] = [
            float(class_probabilities.get(class_id, 0.0))
            for class_id in range(N_CLASSES)
        ]
    return matrix


def train_export_verify(
    data_root: str,
    onnx_path: str,
    report_path: str,
    n_jobs: int,
    verification_rows: int,
) -> dict[str, Any]:
    artifact_path = Path(onnx_path)
    artifact_path.parent.mkdir(parents=True, exist_ok=True)
    report_file = Path(report_path)
    report_file.parent.mkdir(parents=True, exist_ok=True)

    loader = DuckDBFeatureLoader(data_root=data_root)
    raw_df = loader.query_features(interval_ms=1000)

    required_columns = CANONICAL_FEATURES + [TARGET_HORIZON, "grid_nanos"]
    combined = finite_rows(raw_df, required_columns)
    n_dropped = raw_df.height - combined.height

    X = combined.select(CANONICAL_FEATURES).to_numpy().astype(np.float32)
    y = combined.select(TARGET_HORIZON).to_numpy().flatten().astype(int)
    grid_nanos = combined.select("grid_nanos").to_numpy().flatten()

    folds = generate_walk_forward_folds(
        grid_nanos,
        n_folds=3,
        lookback_L=5_000_000_000,
        tau=5_000_000_000,
        embargo=2_000_000_000,
    )
    train_mask, test_mask = folds[-1]
    X_train, y_train = X[train_mask], y[train_mask]
    X_test = X[test_mask]

    print(
        f"Loaded {raw_df.height:,} rows; dropped {n_dropped:,} null/NaN rows; "
        f"train={len(X_train):,}; test={len(X_test):,}; "
        f"features={X_train.shape[1]}"
    )

    print("Training native LightGBM hist classifier...")
    model = train_lightgbm_hist_classifier(X_train, y_train, n_jobs=n_jobs)

    print("Exporting LightGBM Booster to ONNX...")
    onnx_model = convert_lightgbm(
        model,
        initial_types=[("features", FloatTensorType([None, X_train.shape[1]]))],
        target_opset=15,
    )
    patch_dynamic_label_output(onnx_model)
    onnx.checker.check_model(onnx_model)
    onnx.save_model(onnx_model, artifact_path)

    checked_model = onnx.load_model(artifact_path)
    onnx.checker.check_model(checked_model)
    label_spec = output_contract(checked_model, "label")
    probability_spec = output_contract(checked_model, "probabilities")

    if label_spec["shape"] != ["N"]:
        raise AssertionError(
            f"Expected label contract ['N']; got {label_spec['shape']}"
        )
    if probability_spec["onnx_type"] != "sequence<map>":
        raise AssertionError(
            "Expected converter ZipMap output; got "
            f"{probability_spec['onnx_type']}"
        )

    n_verify = min(max(1, verification_rows), len(X_test))
    X_verify = X_test[:n_verify].astype(np.float32, copy=False)
    native_probabilities = model.predict(X_verify)

    session = ort.InferenceSession(
        str(artifact_path),
        providers=["CPUExecutionProvider"],
    )
    input_name = session.get_inputs()[0].name
    runtime_outputs = session.run(None, {input_name: X_verify})
    runtime_labels = np.asarray(runtime_outputs[0])
    onnx_probabilities = zipmap_to_probability_matrix(runtime_outputs)

    if runtime_labels.shape != (n_verify,):
        raise AssertionError(
            f"Expected runtime label shape ({n_verify},); got {runtime_labels.shape}"
        )
    if onnx_probabilities.shape != native_probabilities.shape:
        raise AssertionError(
            f"Probability shape mismatch: native={native_probabilities.shape}; "
            f"onnx={onnx_probabilities.shape}"
        )

    max_abs_error = float(np.max(np.abs(native_probabilities - onnx_probabilities)))
    mean_abs_error = float(np.mean(np.abs(native_probabilities - onnx_probabilities)))
    class_match_rate = float(
        np.mean(
            np.argmax(native_probabilities, axis=1)
            == np.argmax(onnx_probabilities, axis=1)
        )
    )
    verification_passed = bool(
        max_abs_error <= PROBABILITY_TOLERANCE and class_match_rate == 1.0
    )
    if not verification_passed:
        raise AssertionError(
            "Native/ONNX probability equivalence failed: "
            f"max_abs_error={max_abs_error:.8g}; "
            f"class_match_rate={class_match_rate:.6f}; "
            f"tolerance={PROBABILITY_TOLERANCE}"
        )

    artifact_bytes = artifact_path.stat().st_size
    artifact_mib = artifact_bytes / (1024 * 1024)

    report = {
        "stage": "M4P3S1",
        "model_type": "LightGBM multiclass histogram classifier",
        "original_label_space": [-1, 0, 1],
        "onnx_label_mapping": {"-1": 0, "0": 1, "1": 2},
        "onnx_probability_class_keys": {
            "0": "P(original_label=-1)",
            "1": "P(original_label=0)",
            "2": "P(original_label=+1)",
        },
        "input_name": input_name,
        "input_contract": {"dtype": "float32", "shape": ["N", len(CANONICAL_FEATURES)]},
        "label_output_contract": label_spec,
        "probability_output_contract": probability_spec,
        "feature_order": CANONICAL_FEATURES,
        "sampling_interval_ms": 1000,
        "target_horizon": TARGET_HORIZON,
        "data_rows_total": int(raw_df.height),
        "data_rows_dropped_null_or_nan": int(n_dropped),
        "train_rows": int(len(X_train)),
        "test_rows": int(len(X_test)),
        "onnx_path": str(artifact_path),
        "onnx_size_bytes": int(artifact_bytes),
        "onnx_size_mib": artifact_mib,
        "onnx_graph_validated": True,
        "onnxruntime_provider": "CPUExecutionProvider",
        "verification_rows": int(n_verify),
        "native_onnx_max_abs_probability_error": max_abs_error,
        "native_onnx_mean_abs_probability_error": mean_abs_error,
        "native_onnx_class_match_rate": class_match_rate,
        "probability_tolerance": PROBABILITY_TOLERANCE,
        "verification_passed": verification_passed,
        "java_consumer_note": (
            "The probabilities output is ONNX-ML ZipMap: a sequence of maps. "
            "For each input row, retrieve class keys 0, 1, 2 as P(down), "
            "P(neutral), P(up), respectively. M4P3S2 persists this contract."
        ),
        "scope_note": (
            "This validates serialization and inference equivalence on the "
            "locally available dataset; it does not verify the separate "
            "4.5M-row training-time benchmark."
        ),
    }
    report_file.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(f"ONNX artifact: {artifact_path} ({artifact_bytes:,} bytes; {artifact_mib:.3f} MiB)")
    print("ONNX graph validated: True")
    print(f"Input contract: {input_name} float32 [N, {len(CANONICAL_FEATURES)}]")
    print(f"Label output contract: {label_spec}")
    print(f"Probability output contract: {probability_spec}")
    print("Probability format: ONNX-ML ZipMap sequence<map<int64, float>>")
    print(f"ONNX Runtime provider: {report['onnxruntime_provider']}")
    print(f"Verification rows: {n_verify:,}")
    print(f"Native/ONNX max probability error: {max_abs_error:.8g}")
    print(f"Native/ONNX mean probability error: {mean_abs_error:.8g}")
    print(f"Native/ONNX class match rate: {class_match_rate:.6f}")
    print(f"Verification passed (tolerance={PROBABILITY_TOLERANCE}): {verification_passed}")
    print(f"Export report: {report_file}")

    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="M4P3S1 export and verify LightGBM hist classifier as ONNX"
    )
    parser.add_argument("--data-root", default="data/parquet")
    parser.add_argument("--onnx-path", default="data/models/champion_model.onnx")
    parser.add_argument("--report-path", default="data/models/onnx_export_report.json")
    parser.add_argument("--n-jobs", type=int, default=-1)
    parser.add_argument("--verification-rows", type=int, default=256)
    args = parser.parse_args()

    train_export_verify(
        data_root=args.data_root,
        onnx_path=args.onnx_path,
        report_path=args.report_path,
        n_jobs=args.n_jobs,
        verification_rows=args.verification_rows,
    )
