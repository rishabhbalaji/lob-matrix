#!/usr/bin/env python3
"""
Signal Half-Life Decay Curve Plotter & Pearson/Spearman Matrices
Implementation for M4P1S2 task - plots empirical signal decay curves and feature cross-correlation heatmaps.
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
from scipy.stats import spearmanr, pearsonr
from duckdb_feature_loader import DuckDBFeatureLoader

# Feature set from the previous work
CANONICAL_FEATURES = [
    "l1_obi", "total_obi", "w_obi_lin", "w_obi_exp",
    "micro_pressure_bps", "ml_microprice",
    "l1_ofi", "ml_ofi_uniform", "ml_ofi_exp",
    "trade_strength"
]

# Standard intervals for half-life analysis
STANDARD_INTERVALS_MS = [100, 250, 500, 1000, 2000]
STANDARD_HORIZONS_SEC = [1, 5, 10, 30, 60]

def compute_half_life_decay(data: np.ndarray, time_points: np.ndarray) -> tuple:
    """
    Compute half-life decay for a signal - fitting an exponential decay curve.
    Returns the half-life in seconds and the fitted parameters.
    """
    # Remove NaN values
    mask = ~np.isnan(data)
    if not np.any(mask):
        return 0.0, [0.0, 0.0]
    
    valid_data = data[mask]
    valid_times = time_points[mask]
    
    if len(valid_data) < 3:
        return 0.0, [0.0, 0.0]
    
    # Fit exponential decay: y = a * exp(-b * t) + c
    # For simplicity, we'll fit a model where the initial value is 1 (normalized)
    try:
        # We are fitting to: signal(t) ~ exp(-lambda * t)
        # So we take log of signal and fit: log(signal) = -lambda * t + offset
        
        # Use only positive values for log transformation
        pos_mask = valid_data > 0
        if not np.any(pos_mask):
            return 0.0, [0.0, 0.0]
            
        log_signal = np.log(valid_data[pos_mask])
        t_vals = valid_times[pos_mask]
        
        # Linear fit to log(signal) vs time 
        coeffs = np.polyfit(t_vals, log_signal, 1)
        lambda_val = -coeffs[0]  # decay rate (positive value)
        
        if lambda_val <= 0:
            return 0.0, [0.0, 0.0]
            
        half_life_sec = np.log(2) / lambda_val
        
        return float(half_life_sec), [lambda_val, coeffs[1]]
    except:
        return 0.0, [0.0, 0.0]

def plot_signal_decay_curves(data_root: str = "data/parquet", output_dir: str = "data/experiments"):
    """
    Plot empirical signal decay curves from 1s to 60s for each feature.
    """
    out_path = Path(output_dir)
    out_path.mkdir(parents=True, exist_ok=True)
    loader = DuckDBFeatureLoader(data_root=data_root)
    
    print("🧠 Computing Signal Half-Life Decay Curves...")
    
    # Prepare data structures
    feature_decay_data = {feat: [] for feat in CANONICAL_FEATURES}
    decay_curves = {}
    
    # Get decay data by interval and horizon
    for i, delta_t in enumerate(STANDARD_INTERVALS_MS):
        print(f"  📊 Analyzing sampling frequency Delta t = {delta_t}ms...")
        df = loader.query_features(interval_ms=delta_t)
        
        if len(df) == 0:
            print(f"    ⚠️ No data found for {delta_t}ms")
            continue
            
        # For each feature, collect values across horizons
        for feat in CANONICAL_FEATURES:
            if feat not in df.columns:
                continue
                
            # Extract the feature data
            feature_data = df[feat].to_numpy()
            
            # Compute means at different horizons (1s to 60s)
            horizon_means = []
            for tau in STANDARD_HORIZONS_SEC:
                target_col = f"r_{tau}s"
                if target_col not in df.columns:
                    continue
                    
                y_target = df[target_col].to_numpy()
                
                # Use only valid signal pairs
                mask = ~np.isnan(feature_data) & ~np.isnan(y_target)
                if np.sum(mask) < 5:  # Not enough data
                    horizon_means.append(0.0)
                else:
                    # Compute correlation between feature and target return
                    corr, _ = spearmanr(feature_data[mask], y_target[mask])
                    horizon_means.append(float(corr))
                    
            # Store for curve fitting (convert to actual time points)  
            time_points = np.array(STANDARD_HORIZONS_SEC)
            signal_values = np.array(horizon_means)
            
            if any(x != 0.0 for x in horizon_means):
                half_life, params = compute_half_life_decay(signal_values, time_points)
                decay_curves[feat] = {
                    'time_points': time_points.tolist(),
                    'signal_values': signal_values.tolist(),
                    'half_life_sec': half_life,
                    'params': params
                }
    
    # Generate plots for each feature
    plt.figure(figsize=(15, 10))
    for i, (feat, data) in enumerate(decay_curves.items()):
        if i >= 9:  # Limit to 9 features per plot
            break
            
        plt.subplot(3, 3, i+1)
        plt.plot(data['time_points'], data['signal_values'], 'o-', linewidth=2, markersize=6)
        
        plt.title(f"{feat}\nHalf-life: {data['half_life_sec']:.1f}s" if data['half_life_sec'] > 0 else f"{feat}", 
                 fontsize=10, pad=8)
        plt.xlabel("Forward Horizon (seconds)")
        plt.ylabel("Spearman Rank Correlation")
        plt.grid(True, alpha=0.3)
        
        # Set y-axis range from -1 to 1 for better visualization
        plt.ylim(-1.0, 1.0)
        
    plt.suptitle("Signal Half-Life Decay Curves (1s to 60s)",
                fontsize=14, fontweight="bold", y=0.98)
    plt.tight_layout(rect=[0, 0, 1, 0.97])
    
    plot_file = out_path / "signal_decay_curves.png"
    plt.savefig(plot_file, dpi=300, bbox_inches='tight')
    plt.close()
    
    # Save decay data as JSON
    decay_json_file = out_path / "signal_decay_data.json"
    with open(decay_json_file, "w") as f:
        json.dump(decay_curves, f, indent=2)
        
    print(f"✅ Exported signal decay curves to {plot_file}")
    print(f"✅ Exported decay data to {decay_json_file}")

def plot_correlation_matrices(data_root: str = "data/parquet", output_dir: str = "data/experiments"):
    """
    Plot Pearson and Spearman correlation heatmaps for features.
    """
    out_path = Path(output_dir)
    out_path.mkdir(parents=True, exist_ok=True)
    loader = DuckDBFeatureLoader(data_root=data_root)
    
    print("🧠 Computing Feature Correlation Matrices...")
    
    # Get a single interval for correlation analysis 
    interval_ms = 1000
    df = loader.query_features(interval_ms=interval_ms)
    
    if len(df) == 0:
        print(f"  ⚠️ No data found for {interval_ms}ms")
        return
        
    # Get only feature columns to analyze
    feature_cols = [col for col in CANONICAL_FEATURES if col in df.columns]
    
    if len(feature_cols) < 2:
        print("  ⚠️ Not enough features for correlation analysis")
        return
        
    # Prepare data matrix
    feature_matrix = df.select(feature_cols).to_numpy()
    
    # Compute Pearson and Spearman correlations
    n_features = len(feature_cols)
    pearson_corr = np.zeros((n_features, n_features))
    spearman_corr = np.zeros((n_features, n_features))
    
    for i in range(n_features):
        for j in range(n_features):
            x = feature_matrix[:, i]
            y = feature_matrix[:, j]
            
            # Remove NaNs
            mask = ~np.isnan(x) & ~np.isnan(y)
            if np.sum(mask) < 5:
                pearson_corr[i, j] = 0.0
                spearman_corr[i, j] = 0.0
            else:
                p_corr, _ = pearsonr(x[mask], y[mask])
                s_corr, _ = spearmanr(x[mask], y[mask])
                
                # Convert to float for JSON serialization but avoid infinity/nan
                pearson_corr[i, j] = float(p_corr) if not np.isnan(p_corr) and not np.isinf(p_corr) else 0.0
                spearman_corr[i, j] = float(s_corr) if not np.isnan(s_corr) and not np.isinf(s_corr) else 0.0

    # Plot Pearson correlation heatmap
    plt.figure(figsize=(12, 10), dpi=150)
    sns.set_theme(style="white")
    
    mask = np.triu(np.ones_like(pearson_corr, dtype=bool))
    ax = sns.heatmap(
        pearson_corr,
        annot=True,
        fmt=".3f",
        cmap="RdBu_r",
        xticklabels=feature_cols,
        yticklabels=feature_cols,
        cbar_kws={'label': 'Pearson Correlation'},
        mask=mask
    )
    
    plt.title("Feature Pearson Correlation Matrix", fontsize=14, fontweight="bold", pad=15)
    plt.xlabel("Features")
    plt.ylabel("Features")
    
    plot_file = out_path / "pearson_correlation_matrix.png"
    plt.tight_layout()
    plt.savefig(plot_file)
    plt.close()
    
    # Save Pearson data for JSON
    pearson_data_file = out_path / "pearson_correlation_data.json"
    with open(pearson_data_file, "w") as f:
        json.dump({
            "features": feature_cols,
            "correlations": pearson_corr.tolist()
        }, f, indent=2)
        
    # Plot Spearman correlation heatmap (lower triangle to avoid redundancy)
    plt.figure(figsize=(12, 10), dpi=150)
    sns.set_theme(style="white")
    
    mask = np.triu(np.ones_like(spearman_corr, dtype=bool))
    ax = sns.heatmap(
        spearman_corr,
        annot=True,
        fmt=".3f",
        cmap="RdBu_r",
        xticklabels=feature_cols,
        yticklabels=feature_cols,
        cbar_kws={'label': 'Spearman Correlation'},
        mask=mask
    )
    
    plt.title("Feature Spearman Correlation Matrix", fontsize=14, fontweight="bold", pad=15)
    plt.xlabel("Features")
    plt.ylabel("Features")
    
    plot_file = out_path / "spearman_correlation_matrix.png"
    plt.tight_layout()
    plt.savefig(plot_file)
    plt.close()
    
    # Save Spearman data for JSON
    spearman_data_file = out_path / "spearman_correlation_data.json"
    with open(spearman_data_file, "w") as f:
        json.dump({
            "features": feature_cols,
            "correlations": spearman_corr.tolist()
        }, f, indent=2)
    
    print(f"✅ Exported Pearson correlation matrix to {plot_file}")
    print(f"✅ Exported Spearman correlation matrix to {plot_file}")
    print(f"✅ Exported data to {pearson_data_file} and {spearman_data_file}")

def main():
    parser = argparse.ArgumentParser(description="Signal Half-Life Decay Curve Plotter & Correlation Matrices")
    parser.add_argument("--data-root", default="data/parquet", help="Root data directory")
    parser.add_argument("--output-dir", default="data/experiments", help="Output directory")
    args = parser.parse_args()
    
    # Run all analyses
    plot_signal_decay_curves(args.data_root, args.output_dir)
    plot_correlation_matrices(args.data_root, args.output_dir)

if __name__ == "__main__":
    main()