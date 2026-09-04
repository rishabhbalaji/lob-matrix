"""
M4P3S2: Generate versioned feature-scaler and ONNX-model metadata contracts.

Produces:
  data/models/scalerparams.json
  data/models/modelmetadata.json

The scaler is fitted strictly on the final purged walk-forward training fold.
It uses the same 12-feature ordering, 1-second sampling interval, and y_5s
target configuration used by M4P2S2 and M4P3S1. Test rows are not used to
fit normalization statistics.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import onnx
import polars as pl

from duckdb_feature_loader import DuckDBFeatureLoader
from purged_cv_validator import generate_walk_forward_folds


STAGE = "M4P3S2"
SCHEMA_VERSION = 1
MODEL_VERSION = "m4p3s1-lightgbm-hist-v1"
SCALER_VERSION = "m4p3s2-standard-scaler-v1"

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
SAMPLING_INTERVAL_MS = 1000
N_FEATURES = len(CANONICAL_FEATURES)
ZERO_STD_FLOOR = 1e-12


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def finite_rows(df: pl.DataFrame, columns: list[str]) -> pl.DataFrame:
    """Remove both Polars nulls and IEEE float NaNs from selected columns."""
    result = df.select(columns).drop_nulls()
    for column in columns:
        if result.schema[column] in (pl.Float32, pl.Float64):
            result = result.filter(pl.col(column).is_not_nan())
    return result


def dynamic_shape(value_info: onnx.ValueInfoProto) -> list[object]:
    return [
        dimension.dim_param if dimension.dim_param else dimension.dim_value
        for dimension in value_info.type.tensor_type.shape.dim
    ]


def inspect_onnx_contract(model_path: Path) -> dict:
    model = onnx.load_model(model_path)
    onnx.checker.check_model(model)

    graph_inputs = {item.name: item for item in model.graph.input}
    graph_outputs = {item.name: item for item in model.graph.output}

    if "features" not in graph_inputs:
        raise ValueError(f"Expected ONNX input 'features'; found {list(graph_inputs)}")
    if "label" not in graph_outputs:
        raise ValueError(f"Expected ONNX output 'label'; found {list(graph_outputs)}")
    if "probabilities" not in graph_outputs:
        raise ValueError(
            f"Expected ONNX output 'probabilities'; found {list(graph_outputs)}"
        )

    input_shape = dynamic_shape(graph_inputs["features"])
    label_shape = dynamic_shape(graph_outputs["label"])

    probabilities_type = graph_outputs["probabilities"].type
    if not probabilities_type.HasField("sequence_type"):
        raise ValueError("Expected ONNX ZipMap probabilities sequence output")

    map_type = probabilities_type.sequence_type.elem_type.map_type
    if not map_type:
        raise ValueError("Expected ONNX probability sequence element to be a map")

    # onnxmltools may encode a dynamic batch dimension as either the
    # symbolic name "N" or numeric 0. Normalize both valid encodings into
    # the stable deployment contract ["N", 12] for the Java consumer.
    if input_shape not in (["N", N_FEATURES], [0, N_FEATURES]):
        raise ValueError(
            "ONNX input contract mismatch: expected dynamic batch dimension "
            f"and {N_FEATURES} features, got {input_shape}"
        )
    normalized_input_shape = ["N", N_FEATURES]

    if label_shape != ["N"]:
        raise ValueError(
            f"ONNX label contract mismatch: expected ['N'], got {label_shape}"
        )

    return {
        "onnx_graph_validated": True,
        "input": {
            "name": "features",
            "dtype": "float32",
            "shape": normalized_input_shape,
            "raw_onnx_shape": input_shape,
        },
        "outputs": {
            "label": {
                "name": "label",
                "onnx_type": "tensor<int64>",
                "shape": label_shape,
                "remapped_class_values": [0, 1, 2],
            },
            "probabilities": {
                "name": "probabilities",
                "onnx_type": "sequence<map<int64,float32>>",
                "per_row_class_keys": [0, 1, 2],
            },
        },
    }


def generate_contracts(
    data_root: str,
    model_path: str,
    output_dir: str,
) -> tuple[Path, Path]:
    onnx_path = Path(model_path)
    if not onnx_path.exists():
        raise FileNotFoundError(
            f"Missing ONNX artifact: {onnx_path}. Run scripts/export_model_onnx.py first."
        )

    out_dir = Path(output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    scaler_path = out_dir / "scalerparams.json"
    metadata_path = out_dir / "modelmetadata.json"

    loader = DuckDBFeatureLoader(data_root=data_root)
    raw_df = loader.query_features(interval_ms=SAMPLING_INTERVAL_MS)

    required_columns = CANONICAL_FEATURES + [TARGET_HORIZON, "grid_nanos"]
    combined = finite_rows(raw_df, required_columns)
    rows_dropped = raw_df.height - combined.height

    X = combined.select(CANONICAL_FEATURES).to_numpy().astype(np.float64)
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
    X_train = X[train_mask]
    y_train = y[train_mask]

    if X_train.shape[1] != N_FEATURES:
        raise AssertionError(
            f"Expected {N_FEATURES} features; got {X_train.shape[1]}"
        )
    if len(X_train) == 0:
        raise ValueError("Purged fold produced no training rows")

    means = np.mean(X_train, axis=0)
    stds = np.std(X_train, axis=0, ddof=0)
    zero_variance_mask = stds < ZERO_STD_FLOOR
    safe_stds = np.where(zero_variance_mask, 1.0, stds)

    now = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    feature_statistics = []
    for index, feature in enumerate(CANONICAL_FEATURES):
        feature_statistics.append(
            {
                "index": index,
                "name": feature,
                "mean": float(means[index]),
                "std": float(safe_stds[index]),
                "raw_std": float(stds[index]),
                "zero_variance_replaced_with_one": bool(zero_variance_mask[index]),
            }
        )

    scaler = {
        "schema_version": SCHEMA_VERSION,
        "stage": STAGE,
        "scaler_version": SCALER_VERSION,
        "created_at_utc": now,
        "method": "standard_score",
        "formula": "z_i = (x_i - mean_i) / std_i",
        "input_dtype": "float32",
        "n_features": N_FEATURES,
        "feature_order": CANONICAL_FEATURES,
        "feature_statistics": feature_statistics,
        "zero_variance_policy": {
            "std_floor": ZERO_STD_FLOOR,
            "replacement_std": 1.0,
            "reason": "Avoid division by zero during Java inference.",
        },
        "fit_scope": {
            "sampling_interval_ms": SAMPLING_INTERVAL_MS,
            "target_horizon": TARGET_HORIZON,
            "total_rows_loaded": int(raw_df.height),
            "rows_dropped_null_or_nan": int(rows_dropped),
            "usable_rows": int(combined.height),
            "purged_walk_forward_fold": "last_of_3",
            "train_rows_used_for_fit": int(len(X_train)),
            "test_rows_excluded_from_fit": int(np.sum(test_mask)),
            "labels_present_in_training": sorted(int(value) for value in np.unique(y_train)),
        },
    }

    onnx_contract = inspect_onnx_contract(onnx_path)
    metadata = {
        "schema_version": SCHEMA_VERSION,
        "stage": STAGE,
        "model_version": MODEL_VERSION,
        "created_at_utc": now,
        "model": {
            "format": "ONNX",
            "path": str(onnx_path),
            "sha256": sha256_file(onnx_path),
            "size_bytes": int(onnx_path.stat().st_size),
            "producer_stage": "M4P3S1",
            "model_type": "LightGBM multiclass histogram classifier",
            "objective": "multiclass directional return probability",
            "original_label_space": [-1, 0, 1],
            "remapped_label_mapping": {
                "-1": 0,
                "0": 1,
                "1": 2,
            },
            "reverse_label_mapping": {
                "0": -1,
                "1": 0,
                "2": 1,
            },
        },
        "feature_contract": {
            "n_features": N_FEATURES,
            "feature_order": CANONICAL_FEATURES,
            "sampling_interval_ms": SAMPLING_INTERVAL_MS,
            "target_horizon": TARGET_HORIZON,
            "scaler_path": str(scaler_path),
            "scaler_sha256": None,
        },
        "onnx_contract": onnx_contract,
        "probability_interpretation": {
            "class_key_0": "P(original_label=-1): directional down",
            "class_key_1": "P(original_label=0): neutral",
            "class_key_2": "P(original_label=+1): directional up",
            "bullish_score": "P(class_key=2) - P(class_key=0)",
        },
        "provenance": {
            "dataset_rows_total": int(raw_df.height),
            "rows_dropped_null_or_nan": int(rows_dropped),
            "usable_rows": int(combined.height),
            "train_rows": int(np.sum(train_mask)),
            "test_rows": int(np.sum(test_mask)),
            "split": "last fold of 3-fold purged walk-forward validation",
            "benchmark_scope_note": (
                "Generated using the locally available dataset. This metadata "
                "does not validate the separate README 4.5M-row training benchmark."
            ),
        },
        "java_consumer_requirements": {
            "preserve_feature_order_exactly": True,
            "apply_scaler_before_inference": True,
            "cast_input_to_float32": True,
            "onnxruntime_provider_expected": "CPUExecutionProvider",
            "zipmap_decoding": (
                "probabilities is a sequence<map<int64,float32>>. For each row, "
                "retrieve keys 0, 1, and 2 as down, neutral, and up probabilities."
            ),
        },
    }

    scaler_path.write_text(json.dumps(scaler, indent=2, allow_nan=False), encoding="utf-8")
    metadata["feature_contract"]["scaler_sha256"] = sha256_file(scaler_path)
    metadata_path.write_text(
        json.dumps(metadata, indent=2, allow_nan=False),
        encoding="utf-8",
    )

    print(
        f"Loaded {raw_df.height:,} rows; dropped {rows_dropped:,} null/NaN rows; "
        f"usable={combined.height:,}"
    )
    print(
        f"Scaler fit scope: train={len(X_train):,}; "
        f"test excluded={int(np.sum(test_mask)):,}; features={N_FEATURES}"
    )
    print(f"Zero-variance features: {int(np.sum(zero_variance_mask))}")
    print(f"ONNX SHA-256: {metadata['model']['sha256']}")
    print(f"Scaler SHA-256: {metadata['feature_contract']['scaler_sha256']}")
    print(f"Wrote scaler contract: {scaler_path}")
    print(f"Wrote model metadata: {metadata_path}")

    return scaler_path, metadata_path


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="M4P3S2 generate scaler and ONNX model metadata contracts"
    )
    parser.add_argument("--data-root", default="data/parquet")
    parser.add_argument("--model-path", default="data/models/champion_model.onnx")
    parser.add_argument("--output-dir", default="data/models")
    args = parser.parse_args()

    generate_contracts(
        data_root=args.data_root,
        model_path=args.model_path,
        output_dir=args.output_dir,
    )
