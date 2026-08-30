#!/usr/bin/env python3
"""
2D Information Surface Experiment Engine
Computes Spearman Rank Information Coefficients (Rank IC), Information Ratios (IR),
and generates the 2D Information Surface Matrix across Delta t in {100ms..2s} x tau in {1s..60s}.
"""

import os
import json
import argparse
import numpy as np
import polars as pl
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import seaborn as sns
from pathlib import Path
from scipy.stats import spearmanr
from duckdb_feature_loader import DuckDBFeatureLoader

CANONICAL_FEATURES = [
    "l1_obi", "total_obi", "w_obi_lin", "w_obi_exp",
    "micro_pressure_bps", "ml_microprice",
    "l1_ofi", "ml_ofi_uniform", "ml_ofi_exp",
    "trade_strength"
]

STANDARD_INTERVALS_MS = [100, 250, 500, 1000, 2000]
STANDARD_HORIZONS_SEC = [1, 5, 10, 30, 60]

def compute_rank_ic(x: np.ndarray, y: np.ndarray) -> float:
    """
    Computes Spearman Rank Correlation between feature x and forward return y.
    """
    mask = ~np.isnan(x) & ~np.isnan(y) & ~np.isinf(x) & ~np.isinf(y)
    if np.sum(mask) < 30:
        return 0.0
    corr, _ = spearmanr(x[mask], y[mask])
    return float(corr) if not np.isnan(corr) else 0.0

def run_information_surface_experiment(data_root: str = "data/parquet", output_dir: str = "data/experiments"):
    out_path = Path(output_dir)
    out_path.mkdir(parents=True, exist_ok=True)
    loader = DuckDBFeatureLoader(data_root=data_root)

    print("🧠 Starting 2D Information Surface Evaluation...")

    # Matrix: feature -> 2D grid of Rank IC (5 frequencies x 5 horizons)
    surface_matrices = {feat: np.zeros((len(STANDARD_INTERVALS_MS), len(STANDARD_HORIZONS_SEC))) for feat in CANONICAL_FEATURES}

    # Aggregate IC over all features
    composite_matrix = np.zeros((len(STANDARD_INTERVALS_MS), len(STANDARD_HORIZONS_SEC)))

    for i, delta_t in enumerate(STANDARD_INTERVALS_MS):
        print(f"  📊 Analyzing sampling frequency Delta t = {delta_t}ms...")
        df = loader.query_features(interval_ms=delta_t)
        
        if len(df) == 0:
            print(f"    ⚠️ No data found for {delta_t}ms")
            continue

        for j, tau in enumerate(STANDARD_HORIZONS_SEC):
            target_col = f"r_{tau}s"
            if target_col not in df.columns:
                continue

            y_target = df[target_col].to_numpy()
            cell_ics = []

            for feat in CANONICAL_FEATURES:
                if feat not in df.columns:
                    continue
                x_feat = df[feat].to_numpy()
                ic = compute_rank_ic(x_feat, y_target)
                surface_matrices[feat][i, j] = ic
                cell_ics.append(abs(ic))

            composite_matrix[i, j] = np.mean(cell_ics) if cell_ics else 0.0

    # Find optimal coordinates (Delta t*, tau*)
    opt_idx = np.unravel_index(np.argmax(composite_matrix), composite_matrix.shape)
    opt_delta_t = STANDARD_INTERVALS_MS[opt_idx[0]]
    opt_tau = STANDARD_HORIZONS_SEC[opt_idx[1]]
    max_ic = float(composite_matrix[opt_idx])

    print(f"\n🏆 2D Information Surface Optimal Peak Identified:")
    print(f"   • Optimal Sampling Frequency (Delta t*): {opt_delta_t} ms")
    print(f"   • Optimal Forward Horizon (tau*): {opt_tau} s")
    print(f"   • Mean Rank IC Peak: {max_ic:.4f}")

    # Export Matrix JSON
    matrix_export = {
        "sampling_frequencies_ms": STANDARD_INTERVALS_MS,
        "forward_horizons_sec": STANDARD_HORIZONS_SEC,
        "optimal_point": {
            "delta_t_ms": opt_delta_t,
            "tau_sec": opt_tau,
            "peak_rank_ic": max_ic
        },
        "composite_surface": composite_matrix.tolist(),
        "per_feature_surface": {feat: mat.tolist() for feat, mat in surface_matrices.items()}
    }

    json_file = out_path / "information_surface_matrix.json"
    with open(json_file, "w") as f:
        json.dump(matrix_export, f, indent=2)

    # Plot 2D Heatmap & Contour Plot
    plt.figure(figsize=(10, 8), dpi=150)
    sns.set_theme(style="white")
    
    ax = sns.heatmap(
        composite_matrix,
        annot=True,
        fmt=".4f",
        cmap="viridis",
        xticklabels=[f"{tau}s" for tau in STANDARD_HORIZONS_SEC],
        yticklabels=[f"{dt}ms" for dt in STANDARD_INTERVALS_MS],
        cbar_kws={'label': 'Mean Absolute Rank IC'}
    )
    
    plt.title("2D Information Surface: Rank IC(Δt, τ) Across Microstructure Scales", fontsize=14, fontweight="bold", pad=15)
    plt.xlabel("Forward Forecast Horizon (τ)", fontsize=12, labelpad=10)
    plt.ylabel("Sampling Frequency (Δt)", fontsize=12, labelpad=10)
    
    # Highlight optimal cell
    rect = plt.Rectangle((opt_idx[1], opt_idx[0]), 1, 1, fill=False, edgecolor='red', lw=3)
    ax.add_patch(rect)

    plot_file = out_path / "information_surface_heatmap.png"
    plt.tight_layout()
    plt.savefig(plot_file)
    plt.close()

    print(f"✅ Exported matrix to {json_file}")
    print(f"✅ Exported heatmap plot to {plot_file}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="2D Information Surface Engine")
    parser.add_argument("--data-root", default="data/parquet", help="Root data directory")
    parser.add_argument("--output-dir", default="data/experiments", help="Output directory")
    args = parser.parse_args()

    run_information_surface_experiment(args.data_root, args.output_dir)
