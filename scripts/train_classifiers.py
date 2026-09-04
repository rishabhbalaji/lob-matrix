"""
M4P2S2: Train LightGBM / XGBoost `hist` Multi-Factor Classifiers

Objective (README.md, M4P2S2):
    Train non-linear decision tree models predicting directional return
    probabilities.
Verification (README.md, M4P2S2):
    Training on 4.5 million rows completes in <45s on Intel i5 CPU with
    zero GPU.

FIXED VERSION -- changes from the original skeleton:
  1. build_feature_matrix now drops both Polars `null` AND float `NaN`
     values. `.drop_nulls()` alone does NOT remove NaN floats -- Polars
     treats null and NaN as distinct concepts. This was silently letting
     NaN rows in `r_5s` (continuous forward return) through, which
     poisoned any downstream Spearman IC calculation with `nan`.
  2. Added evaluate_classifier_rank_ic(), which reports Spearman Rank IC
     of the model's predicted "bullish score" (P(up) - P(down)) against
     the actual continuous forward return. This matches the project's
     own Q13 evaluation standard ("Out-of-sample Spearman Rank IC with
     t > 3.0"), which is a far more appropriate metric here than raw
     3-class accuracy -- accuracy on a noisy, threshold-discretized
     label with an imbalanced neutral class is known to be an
     insensitive metric even when real signal is present.
  3. Fixed indentation / duplicated-line bugs in the __main__ block.
"""

from typing import Tuple
import time
import numpy as np
import polars as pl


def build_feature_matrix(
    df: pl.DataFrame,
    feature_columns: list,
    target_column: str,
) -> Tuple[np.ndarray, np.ndarray]:
    """
    Extracts X (feature_columns) and y (target_column, tri-class {-1,0,1})
    from df as numpy arrays, dropping rows with NaN in either X or y.
    Returns (X, y).
    """
    subset = df.select(feature_columns + [target_column])

    # Drop Polars nulls first.
    subset = subset.drop_nulls()

    # Then drop float NaN sentinels -- Polars does NOT consider NaN a
    # null, so `.drop_nulls()` alone will not catch them.
    float_cols = [
        c for c in feature_columns + [target_column]
        if subset.schema[c] in (pl.Float32, pl.Float64)
    ]
    for c in float_cols:
        subset = subset.filter(pl.col(c).is_not_nan())

    X = subset.select(feature_columns).to_numpy()
    y = subset.select(target_column).to_numpy().flatten().astype(int)

    return X, y


def train_lightgbm_hist_classifier(
    X_train: np.ndarray,
    y_train: np.ndarray,
    n_jobs: int = -1,
):
    """
    Trains a LightGBM multi-class classifier (3 classes: -1, 0, +1) using
    CPU multi-threading (n_jobs). LightGBM's default tree learner is
    already histogram-based, matching the README's "hist" requirement.
    Remaps labels {-1,0,1} -> {0,1,2} for LightGBM's classifier before
    fitting, and remembers the mapping so predictions can be mapped back.
    Returns the fitted model object.
    """
    import lightgbm as lgb

    y_train_remapped = (y_train + 1).astype(int)  # -1->0, 0->1, 1->2

    train_data = lgb.Dataset(X_train, label=y_train_remapped)

    params = {
        'objective': 'multiclass',
        'num_class': 3,
        'boosting_type': 'gbdt',
        'num_leaves': 31,
        'learning_rate': 0.1,
        'feature_fraction': 0.9,
        'bagging_fraction': 0.8,
        'bagging_freq': 5,
        'verbose': -1,
        'n_jobs': n_jobs,
    }

    model = lgb.train(params, train_data, num_boost_round=100)

    model.__dict__['label_mapping'] = {-1: 0, 0: 1, 1: 2}
    model.__dict__['reverse_label_mapping'] = {0: -1, 1: 0, 2: 1}

    return model


def train_xgboost_hist_classifier(
    X_train: np.ndarray,
    y_train: np.ndarray,
    n_jobs: int = -1,
):
    """
    Trains an XGBoost multi-class classifier with tree_method='hist'
    (explicitly, not the default) and CPU multi-threading (n_jobs).
    Remaps labels {-1,0,1} -> {0,1,2} for XGBoost before fitting.
    Returns the fitted model object.
    """
    import xgboost as xgb

    y_train_remapped = (y_train + 1).astype(int)  # -1->0, 0->1, 1->2

    model = xgb.XGBClassifier(
        objective='multi:softprob',
        num_class=3,
        tree_method='hist',
        n_estimators=100,
        n_jobs=n_jobs,
        random_state=42,
    )

    model.fit(X_train, y_train_remapped)

    model.__dict__['label_mapping'] = {-1: 0, 0: 1, 1: 2}
    model.__dict__['reverse_label_mapping'] = {0: -1, 1: 0, 2: 1}

    return model


def benchmark_training_time(
    train_fn,
    X_train: np.ndarray,
    y_train: np.ndarray,
    **kwargs,
) -> dict:
    """
    Times a single call to train_fn(X_train, y_train, **kwargs) using
    time.perf_counter(). Returns:
        {"model": <fitted model>, "elapsed_sec": <float>,
         "n_rows": len(X_train), "n_features": X_train.shape[1]}
    """
    start_time = time.perf_counter()
    model = train_fn(X_train, y_train, **kwargs)
    end_time = time.perf_counter()

    elapsed_sec = end_time - start_time

    return {
        "model": model,
        "elapsed_sec": elapsed_sec,
        "n_rows": len(X_train),
        "n_features": X_train.shape[1],
    }


def _predict_proba_any(model, X_test: np.ndarray) -> np.ndarray:
    """
    Returns a (n_samples, 3) probability matrix in remapped {0,1,2} class
    order, for either a raw LightGBM Booster or an XGBClassifier.
    """
    if hasattr(model, "predict_proba"):
        return model.predict_proba(X_test)
    # Raw LightGBM Booster: .predict() with objective=multiclass already
    # returns per-class probabilities.
    return model.predict(X_test)


def evaluate_classifier(model, X_test: np.ndarray, y_test: np.ndarray) -> dict:
    """
    Evaluates a fitted classifier on held-out data. Returns a dict with at
    minimum: {"accuracy": ..., "n_test_samples": len(y_test)}.
    Remaps predictions back to {-1,0,1} if the model was trained on
    remapped {0,1,2} labels, so accuracy is computed against the ORIGINAL
    label space, not the remapped one.
    """
    raw_pred = model.predict(X_test)

    if isinstance(raw_pred, np.ndarray) and raw_pred.ndim > 1:
        y_pred_remapped = np.argmax(raw_pred, axis=1)
    else:
        y_pred_remapped = raw_pred

    reverse_map = model.__dict__.get('reverse_label_mapping')
    if reverse_map is not None:
        y_pred_original = np.array(
            [reverse_map[int(p)] for p in y_pred_remapped]
        )
    else:
        y_pred_original = y_pred_remapped

    accuracy = float(np.mean(y_pred_original == y_test))

    return {
        "accuracy": accuracy,
        "n_test_samples": len(y_test),
    }


def evaluate_classifier_rank_ic(model, X_test: np.ndarray, r_test: np.ndarray) -> dict:
    """
    Computes Spearman Rank IC between the model's predicted "bullish
    score" (P(class=+1) - P(class=-1), derived from predicted
    probabilities in remapped {0,1,2} space) and the actual continuous
    forward return r_test. This matches the project's Q13 evaluation
    standard (Out-of-sample Spearman Rank IC, t > 3.0) and is a more
    appropriate skill metric than raw discrete accuracy for a noisy,
    threshold-discretized tri-class label.

    r_test must already be filtered of NaN values by the caller (use
    build_feature_matrix-style NaN filtering upstream).

    Returns {"rank_ic": float, "t_stat": float, "n_test_samples": int}.
    """
    from scipy.stats import spearmanr

    proba = _predict_proba_any(model, X_test)
    score = proba[:, 2] - proba[:, 0]  # remapped 2=+1, 0=-1

    ic, _ = spearmanr(score, r_test)
    n = len(r_test)

    if n > 2 and not np.isnan(ic) and abs(ic) < 1.0:
        t_stat = ic * np.sqrt(n - 2) / np.sqrt(1 - ic ** 2)
    else:
        t_stat = float('nan')

    return {
        "rank_ic": float(ic),
        "t_stat": float(t_stat),
        "n_test_samples": n,
    }


if __name__ == "__main__":
    import sys
    from pathlib import Path
    sys.path.append(str(Path(__file__).parent))
    from duckdb_feature_loader import DuckDBFeatureLoader
    from purged_cv_validator import generate_walk_forward_folds

    CANONICAL_FEATURES = [
        "l1_obi", "total_obi", "w_obi_lin", "w_obi_exp",
        "microprice", "micro_pressure", "l1_ofi",
        "ml_ofi_uniform", "ml_ofi_exp", "trade_strength",
        "rel_spread_bps", "snapshot_age_ms",
    ]
    TARGET_HORIZON = "y_5s"
    RETURN_HORIZON = "r_5s"

    loader = DuckDBFeatureLoader(data_root="data/parquet")
    df = loader.query_features(interval_ms=1000)
    print(f"Loaded {df.shape[0]} total rows (README benchmark target is "
          f"4,500,000 rows -- reporting the honest gap since this is smaller).")

    all_cols = CANONICAL_FEATURES + [TARGET_HORIZON, RETURN_HORIZON, "grid_nanos"]
    combined = df.select(all_cols).drop_nulls()
    for c in all_cols:
        if combined.schema[c] in (pl.Float32, pl.Float64):
            combined = combined.filter(pl.col(c).is_not_nan())

    n_dropped = df.shape[0] - combined.shape[0]
    print(f"Dropped {n_dropped} rows with null/NaN in feature, target, "
          f"or return columns. Remaining: {combined.shape[0]} rows.")

    X = combined.select(CANONICAL_FEATURES).to_numpy()
    y = combined.select(TARGET_HORIZON).to_numpy().flatten().astype(int)
    r = combined.select(RETURN_HORIZON).to_numpy().flatten()
    grid_nanos = combined.select("grid_nanos").to_numpy().flatten()

    folds = generate_walk_forward_folds(
        grid_nanos, n_folds=3, lookback_L=5_000_000_000,
        tau=5_000_000_000, embargo=2_000_000_000,
    )
    train_mask, test_mask = folds[-1]
    train_idx = np.where(train_mask)[0]
    test_idx = np.where(test_mask)[0]

    X_train, y_train = X[train_idx], y[train_idx]
    X_test, y_test = X[test_idx], y[test_idx]
    r_test = r[test_idx]

    print(f"Purged split: train={len(X_train)} rows, test={len(X_test)} rows")

    for name, train_fn in [
        ("LightGBM (hist)", train_lightgbm_hist_classifier),
        ("XGBoost (hist)", train_xgboost_hist_classifier),
    ]:
        bench = benchmark_training_time(train_fn, X_train, y_train)
        acc_result = evaluate_classifier(bench["model"], X_test, y_test)
        ic_result = evaluate_classifier_rank_ic(bench["model"], X_test, r_test)

        target_met = "MET" if bench["elapsed_sec"] < 45.0 else "NOT MET"
        print(
            f"{name}: n_rows={bench['n_rows']:,} elapsed={bench['elapsed_sec']:.3f}s "
            f"(<45s target: {target_met} at this scale -- README's stated "
            f"benchmark is 4,500,000 rows, actual tested scale is "
            f"{bench['n_rows']:,} rows) "
            f"test_accuracy={acc_result['accuracy']:.4f} "
            f"rank_ic={ic_result['rank_ic']:.4f} t_stat={ic_result['t_stat']:.2f}"
        )
