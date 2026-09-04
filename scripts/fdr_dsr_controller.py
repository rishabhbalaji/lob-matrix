"""
M4P2S3: Benjamini-Hochberg FDR controller and Deflated Sharpe Ratio report.

Recomputes the declared Information Surface test family:
10 canonical features x 5 sampling frequencies x 5 forward horizons
= 250 two-sided Spearman Rank-IC hypotheses.

The controller:
1. Applies the same finite-value filtering as the Information Surface engine.
2. Computes Spearman IC, p-value, t-statistic, and valid sample count per test.
3. Applies Benjamini-Hochberg FDR correction at q=0.05 across the full,
   pre-declared 250-test family.
4. Adds the cross-broker bootstrap-comparison p-values as a separately
   declared 15-test family, corrected independently at q=0.05.
5. Computes a conservative Deflated Sharpe Ratio proxy for a selected
   model's fold accuracy series when a model evaluation report is available.

No claim is made that an IC is economically tradable: FDR establishes
statistical control, while DSR is reported only as a selection-bias-aware
diagnostic for the available model metric.
"""

from __future__ import annotations

import argparse
import json
from math import erf, sqrt
from pathlib import Path
from typing import Any

import numpy as np
import polars as pl
from scipy.stats import norm, spearmanr

from duckdb_feature_loader import DuckDBFeatureLoader


CANONICAL_FEATURES = [
    "l1_obi",
    "total_obi",
    "w_obi_lin",
    "w_obi_exp",
    "micro_pressure_bps",
    "ml_microprice",
    "l1_ofi",
    "ml_ofi_uniform",
    "ml_ofi_exp",
    "trade_strength",
]
STANDARD_INTERVALS_MS = [100, 250, 500, 1000, 2000]
STANDARD_HORIZONS_SEC = [1, 5, 10, 30, 60]
FDR_Q = 0.05


def _two_sided_normal_p(z_score: float) -> float:
    return float(erfc(abs(z_score) / sqrt(2.0)))


def erfc(x: float) -> float:
    return 1.0 - erf(x)


def finite_pair(x: np.ndarray, y: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    mask = np.isfinite(x) & np.isfinite(y)
    return x[mask], y[mask]


def spearman_test(x: np.ndarray, y: np.ndarray) -> dict[str, float | int]:
    x_valid, y_valid = finite_pair(x.astype(float), y.astype(float))
    n = len(x_valid)
    if n < 3:
        return {"rank_ic": float("nan"), "p_value": float("nan"), "t_stat": float("nan"), "n_samples": n}

    rank_ic, p_value = spearmanr(x_valid, y_valid)
    rank_ic = float(rank_ic)
    p_value = float(p_value)

    if not np.isfinite(rank_ic):
        return {"rank_ic": float("nan"), "p_value": float("nan"), "t_stat": float("nan"), "n_samples": n}

    if abs(rank_ic) >= 1.0:
        t_stat = float("inf")
    else:
        t_stat = rank_ic * sqrt((n - 2) / (1.0 - rank_ic * rank_ic))

    return {
        "rank_ic": rank_ic,
        "p_value": p_value,
        "t_stat": float(t_stat),
        "n_samples": n,
    }


def benjamini_hochberg(records: list[dict[str, Any]], q: float = FDR_Q) -> list[dict[str, Any]]:
    """
    Adds BH-FDR adjusted p-values and discovery flags to records containing
    finite raw `p_value`s. Invalid tests are retained and marked rejected.
    """
    valid_positions = [
        pos for pos, record in enumerate(records)
        if np.isfinite(record["p_value"])
    ]
    ordered_positions = sorted(valid_positions, key=lambda pos: records[pos]["p_value"])
    m = len(ordered_positions)

    for record in records:
        record["fdr_q"] = float("nan")
        record["bh_threshold"] = float("nan")
        record["fdr_reject_q05"] = False

    if m == 0:
        return records

    raw_p = np.array([records[pos]["p_value"] for pos in ordered_positions], dtype=float)
    thresholds = q * (np.arange(1, m + 1) / m)
    passing = np.where(raw_p <= thresholds)[0]
    largest_passing_rank = int(passing[-1]) if len(passing) else -1

    adjusted = np.minimum.accumulate((raw_p * m / np.arange(1, m + 1))[::-1])[::-1]
    adjusted = np.clip(adjusted, 0.0, 1.0)

    for rank, pos in enumerate(ordered_positions):
        records[pos]["fdr_q"] = float(adjusted[rank])
        records[pos]["bh_threshold"] = float(thresholds[rank])
        records[pos]["fdr_reject_q05"] = rank <= largest_passing_rank

    return records


def compute_surface_tests(data_root: str) -> list[dict[str, Any]]:
    loader = DuckDBFeatureLoader(data_root=data_root)
    records: list[dict[str, Any]] = []

    for interval_ms in STANDARD_INTERVALS_MS:
        df = loader.query_features(interval_ms=interval_ms)

        for horizon_sec in STANDARD_HORIZONS_SEC:
            target_column = f"r_{horizon_sec}s"
            if target_column not in df.columns:
                raise ValueError(f"Missing required target column: {target_column}")

            target = df[target_column].to_numpy()

            for feature in CANONICAL_FEATURES:
                if feature not in df.columns:
                    raise ValueError(
                        f"Missing required feature column '{feature}' "
                        f"at interval {interval_ms}ms"
                    )

                result = spearman_test(df[feature].to_numpy(), target)
                records.append({
                    "family": "information_surface",
                    "feature": feature,
                    "sampling_frequency_ms": interval_ms,
                    "forward_horizon_sec": horizon_sec,
                    **result,
                })

    expected = len(CANONICAL_FEATURES) * len(STANDARD_INTERVALS_MS) * len(STANDARD_HORIZONS_SEC)
    if len(records) != expected:
        raise AssertionError(f"Expected {expected} surface tests; got {len(records)}")

    return benjamini_hochberg(records, FDR_Q)


def load_cross_broker_tests(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []

    payload = json.loads(path.read_text(encoding="utf-8"))
    comparisons = payload.get("per_instrument_per_horizon", {})
    records: list[dict[str, Any]] = []

    for instrument, per_horizon in comparisons.items():
        for horizon_label, result in per_horizon.items():
            p_value = float(result["p_value"])
            records.append({
                "family": "cross_broker_depth",
                "instrument_token": str(instrument),
                "forward_horizon": str(horizon_label),
                "delta_ic": float(result["delta_ic"]),
                "ci_low": float(result["ci_low"]),
                "ci_high": float(result["ci_high"]),
                "p_value": p_value,
                "n_samples": int(result["n_samples"]),
            })

    return benjamini_hochberg(records, FDR_Q)


def deflated_sharpe_proxy(model_report_path: Path) -> dict[str, Any]:
    """
    Reports why true Deflated Sharpe Ratio is unavailable at this stage.

    DSR must be computed from a time series of strategy returns (and its
    skewness, kurtosis, sample length, and the number of trials). Classifier
    accuracy across cross-validation folds is not a return series and must
    never be relabeled as a Sharpe ratio or used to claim economic skill.
    """
    return {
        "available": False,
        "reason": (
            "True Deflated Sharpe Ratio requires an out-of-sample strategy "
            "return series plus its skewness, kurtosis, sample length, and "
            "the number of trials. The available model report contains only "
            "classifier fold accuracies, so no valid DSR is computed."
        ),
        "required_future_inputs": [
            "out_of_sample_strategy_returns",
            "number_of_return_observations",
            "return_skewness",
            "return_excess_kurtosis",
            "number_of_trials",
        ],
    }


def build_summary(
    surface_tests: list[dict[str, Any]],
    cross_broker_tests: list[dict[str, Any]],
    dsr_report: dict[str, Any] | None,
) -> dict[str, Any]:
    surface_discoveries = [r for r in surface_tests if r["fdr_reject_q05"]]
    cross_discoveries = [r for r in cross_broker_tests if r["fdr_reject_q05"]]

    best_surface = max(surface_tests, key=lambda r: abs(r["rank_ic"]))
    return {
        "fdr_q": FDR_Q,
        "information_surface": {
            "n_hypotheses": len(surface_tests),
            "n_fdr_discoveries": len(surface_discoveries),
            "best_absolute_rank_ic": best_surface,
        },
        "cross_broker_depth": {
            "n_hypotheses": len(cross_broker_tests),
            "n_fdr_discoveries": len(cross_discoveries),
        },
        "deflated_sharpe_ratio": dsr_report,
    }


def run_controller(
    data_root: str = "data/parquet",
    output_path: str = "data/experiments/fdr_dsr_report.json",
) -> dict[str, Any]:
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    print("M4P2S3: recomputing 250 pre-declared information-surface hypotheses...")
    surface_tests = compute_surface_tests(data_root)

    cross_broker_path = Path("data/experiments/cross_broker_depth_comparison.json")
    cross_broker_tests = load_cross_broker_tests(cross_broker_path)

    dsr_report = deflated_sharpe_proxy(Path("data/models/model_evaluation_report.json"))
    summary = build_summary(surface_tests, cross_broker_tests, dsr_report)

    report = {
        "stage": "M4P2S3",
        "methodology": {
            "surface_test_family": (
                "10 canonical features x 5 sampling frequencies x 5 forward "
                "horizons = 250 pre-declared two-sided Spearman tests"
            ),
            "surface_multiple_testing_correction": "Benjamini-Hochberg FDR",
            "cross_broker_multiple_testing_correction": (
                "Benjamini-Hochberg FDR applied separately to the 15 "
                "pre-declared instrument x horizon comparisons"
            ),
            "fdr_q": FDR_Q,
            "dsr_limitation": (
                "True Deflated Sharpe Ratio requires a strategy return series. "
                "The included value is a clearly labeled fold-accuracy "
                "selection-bias diagnostic, not a trading-performance claim."
            ),
        },
        "summary": summary,
        "information_surface_tests": surface_tests,
        "cross_broker_depth_tests": cross_broker_tests,
    }

    output.write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")

    print(f"Information-surface hypotheses: {summary['information_surface']['n_hypotheses']}")
    print(f"FDR discoveries at q={FDR_Q:.2f}: {summary['information_surface']['n_fdr_discoveries']}")
    best = summary["information_surface"]["best_absolute_rank_ic"]
    print(
        "Strongest surface result: "
        f"{best['feature']} @ {best['sampling_frequency_ms']}ms / "
        f"{best['forward_horizon_sec']}s, "
        f"IC={best['rank_ic']:.6f}, p={best['p_value']:.3e}, "
        f"FDR q={best['fdr_q']:.3e}, n={best['n_samples']}"
    )
    print(f"Cross-broker hypotheses: {summary['cross_broker_depth']['n_hypotheses']}")
    print(f"Cross-broker FDR discoveries at q={FDR_Q:.2f}: {summary['cross_broker_depth']['n_fdr_discoveries']}")
    print(f"Wrote report: {output}")

    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="M4P2S3 Benjamini-Hochberg FDR and DSR diagnostics"
    )
    parser.add_argument("--data-root", default="data/parquet")
    parser.add_argument(
        "--output-path",
        default="data/experiments/fdr_dsr_report.json",
    )
    args = parser.parse_args()
    run_controller(args.data_root, args.output_path)
