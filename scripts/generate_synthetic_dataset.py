#!/usr/bin/env python3
"""
Synthetic Market Depth & 2D Information Surface Dataset Generator
Simulates realistic multi-day high-frequency LOB microstructure dynamics across all 5 sampling frequencies:
Delta t in {100ms, 250ms, 500ms, 1000ms, 2000ms}.
"""

import os
import argparse
import numpy as np
import polars as pl
from pathlib import Path
from datetime import date, timedelta

# Instrument Universe
INSTRUMENT_META = {
    738561: {"symbol": "RELIANCE", "base_price": 2500.0, "tick_size": 0.05, "spread_ticks": 1},
    1333:   {"symbol": "HDFCBANK", "base_price": 1650.0, "tick_size": 0.05, "spread_ticks": 1},
    1594:   {"symbol": "INFY",     "base_price": 1500.0, "tick_size": 0.05, "spread_ticks": 1}
}

STANDARD_INTERVALS_MS = [100, 250, 500, 1000, 2000]

def generate_microstructure_series(n_steps: int, base_price: float, tick_size: float, interval_ms: int, rng: np.random.Generator):
    """
    Generates realistic order book dynamics where order book imbalance and OFI lead price returns.
    """
    # 1. Latent Order Flow / Imbalance State (AR(1) Mean-Reverting Process)
    phi = 0.85 # High persistence
    innovations = rng.normal(0, 0.3, size=n_steps)
    latent_imbalance = np.zeros(n_steps)
    for t in range(1, n_steps):
        latent_imbalance[t] = phi * latent_imbalance[t - 1] + innovations[t]
        
    # Scale to [-0.95, +0.95] for OBI
    l1_obi = np.tanh(latent_imbalance)
    total_obi = l1_obi * rng.uniform(0.7, 0.9, size=n_steps)
    w_obi_lin = l1_obi * rng.uniform(0.85, 0.95, size=n_steps)
    w_obi_exp = l1_obi * rng.uniform(0.88, 0.98, size=n_steps)

    # 2. Mid-Price Process with Alpha Impact from OBI
    # Price returns have a drift proportional to past OBI + random noise (microstructure signal)
    dt_sec = interval_ms / 1000.0
    alpha_coupling = 0.00015 * np.sqrt(dt_sec) # Imbalance predicts forward price return
    volatility = 0.00030 * np.sqrt(dt_sec)
    
    price_innovations = rng.normal(0, volatility, size=n_steps)
    log_returns = alpha_coupling * np.roll(l1_obi, 1) + price_innovations
    log_returns[0] = 0.0
    
    mid_price = base_price * np.exp(np.cumsum(log_returns))
    # Round to tick size
    mid_price = np.round(mid_price / tick_size) * tick_size

    # 3. Best Bids and Asks
    half_spread = tick_size * 0.5
    spread = tick_size * np.ones(n_steps)
    best_bid = mid_price - half_spread
    best_ask = mid_price + half_spread
    rel_spread_bps = (spread / mid_price) * 10000.0

    # 4. Microprice & Pressures
    # P_micro = P_mid + (l1_obi * half_spread)
    microprice = mid_price + (l1_obi * half_spread * 0.8)
    micro_pressure = microprice - mid_price
    micro_pressure_bps = (micro_pressure / mid_price) * 10000.0
    ml_microprice = mid_price + (w_obi_lin * half_spread * 0.75)

    # 5. Order Flow Imbalance (OFI) & Trade Strength
    delta_mid = np.diff(mid_price, prepend=mid_price[0])
    l1_ofi = 100.0 * (l1_obi - np.roll(l1_obi, 1)) + 500.0 * np.sign(delta_mid)
    ml_ofi_uniform = l1_ofi * 0.8 + rng.normal(0, 20, size=n_steps)
    ml_ofi_exp = l1_ofi * 0.9 + rng.normal(0, 15, size=n_steps)

    trade_strength = np.clip(l1_obi + rng.normal(0, 0.2, size=n_steps), -1.0, 1.0)
    buy_pressure = (trade_strength + 1.0) / 2.0
    sell_pressure = 1.0 - buy_pressure

    # 6. Volumes & VWAP
    ltp = mid_price
    cum_volume = np.cumsum(rng.integers(50, 500, size=n_steps))
    vwap = mid_price + rng.normal(0, 0.1, size=n_steps)

    # 7. Clocks & Timestamps
    grid_seq = np.arange(1, n_steps + 1, dtype=np.int64)
    grid_nanos = grid_seq * int(interval_ms * 1_000_000)
    delta_nanos = np.full(n_steps, int(interval_ms * 1_000_000), dtype=np.int64)
    snapshot_age_ms = rng.uniform(0.5, 12.0, size=n_steps) # 0.5ms to 12ms fresh

    # 8. Multi-Horizon Forward Targets: {1s, 5s, 10s, 30s, 60s}
    horizons_sec = [1, 5, 10, 30, 60]
    return_targets = {}
    label_targets = {}
    exec_targets = {}

    cost_friction = 0.000824 # ~8.24 bps statutory round-trip cost

    for tau_sec in horizons_sec:
        steps_ahead = max(1, int(tau_sec / (interval_ms / 1000.0)))
        
        # Forward Log Return: ln(P_mid(t + tau) / P_mid(t))
        forward_mid = np.roll(mid_price, -steps_ahead)
        r_tau = np.log(forward_mid / mid_price)
        r_tau[-steps_ahead:] = np.nan # Causal boundary mask
        return_targets[f"r_{tau_sec}s"] = r_tau

        # Horizon-Scaled Volatility Threshold theta_tau
        rolling_sigma = 0.0004
        theta_tau = max(0.5 * rolling_sigma * np.sqrt(tau_sec / dt_sec), (tick_size / (2.0 * base_price)))
        
        # Tri-Class Directional Label {-1, 0, +1}
        y_tau = np.zeros(n_steps, dtype=np.int32)
        y_tau[r_tau > theta_tau] = 1
        y_tau[r_tau < -theta_tau] = -1
        y_tau[np.isnan(r_tau)] = 0
        label_targets[f"y_{tau_sec}s"] = y_tau

        # Net Executable Spread-Crossed Return
        forward_bid = forward_mid - half_spread
        exec_gross = (forward_bid - best_ask) / best_ask
        exec_net = exec_gross - cost_friction
        exec_net[-steps_ahead:] = np.nan
        exec_targets[f"exec_{tau_sec}s"] = exec_net

    return {
        "grid_seq": grid_seq,
        "grid_nanos": grid_nanos,
        "delta_nanos": delta_nanos,
        "snapshot_age_ms": snapshot_age_ms,
        "best_bid": best_bid,
        "best_ask": best_ask,
        "mid_price": mid_price,
        "spread": spread,
        "rel_spread_bps": rel_spread_bps,
        "ltp": ltp,
        "cum_volume": cum_volume,
        "vwap": vwap,
        "l1_obi": l1_obi,
        "total_obi": total_obi,
        "w_obi_lin": w_obi_lin,
        "w_obi_exp": w_obi_exp,
        "microprice": microprice,
        "micro_pressure": micro_pressure,
        "micro_pressure_bps": micro_pressure_bps,
        "ml_microprice": ml_microprice,
        "l1_ofi": l1_ofi,
        "ml_ofi_uniform": ml_ofi_uniform,
        "ml_ofi_exp": ml_ofi_exp,
        "trade_strength": trade_strength,
        "buy_pressure": buy_pressure,
        "sell_pressure": sell_pressure,
        **return_targets,
        **label_targets,
        **exec_targets
    }

def generate_multi_day_dataset(output_dir: str, num_days: int = 3, tokens: list = None, rows_per_day: int = 5000):
    output_path = Path(output_dir)
    tokens = tokens or [738561, 1333, 1594]
    start_date = date(2026, 8, 25)
    rng = np.random.default_rng(42)

    total_files = 0
    total_rows = 0

    print(f"🚀 Generating Synthetic LOB Microstructure Dataset across {num_days} days, {len(tokens)} tokens, and 5 frequencies...")

    for day_idx in range(num_days):
        trade_date = start_date + timedelta(days=day_idx)
        date_str = trade_date.strftime("%Y-%m-%d")

        for token in tokens:
            meta = INSTRUMENT_META[token]
            symbol = meta["symbol"]
            base_price = meta["base_price"]
            tick_size = meta["tick_size"]

            token_dir = output_path / f"date={date_str}" / f"instrument_token={token}"
            token_dir.mkdir(parents=True, exist_ok=True)

            for interval_ms in STANDARD_INTERVALS_MS:
                # Scale rows proportionally to frequency (100ms has 10x rows of 1000ms)
                n_rows = max(500, int(rows_per_day * (1000.0 / interval_ms)))
                series = generate_microstructure_series(n_rows, base_price, tick_size, interval_ms, rng)

                df = pl.DataFrame({
                    "grid_seq": series["grid_seq"],
                    "grid_nanos": series["grid_nanos"],
                    "delta_nanos": series["delta_nanos"],
                    "instrument_token": np.full(n_rows, token, dtype=np.int64),
                    "symbol": [symbol] * n_rows,
                    "snapshot_age_ms": series["snapshot_age_ms"],
                    "best_bid": series["best_bid"],
                    "best_ask": series["best_ask"],
                    "mid_price": series["mid_price"],
                    "spread": series["spread"],
                    "rel_spread_bps": series["rel_spread_bps"],
                    "ltp": series["ltp"],
                    "cum_volume": series["cum_volume"],
                    "vwap": series["vwap"],
                    "l1_obi": series["l1_obi"],
                    "total_obi": series["total_obi"],
                    "w_obi_lin": series["w_obi_lin"],
                    "w_obi_exp": series["w_obi_exp"],
                    "microprice": series["microprice"],
                    "micro_pressure": series["micro_pressure"],
                    "micro_pressure_bps": series["micro_pressure_bps"],
                    "ml_microprice": series["ml_microprice"],
                    "l1_ofi": series["l1_ofi"],
                    "ml_ofi_uniform": series["ml_ofi_uniform"],
                    "ml_ofi_exp": series["ml_ofi_exp"],
                    "trade_strength": series["trade_strength"],
                    "buy_pressure": series["buy_pressure"],
                    "sell_pressure": series["sell_pressure"],
                    "r_1s": series["r_1s"],
                    "r_5s": series["r_5s"],
                    "r_10s": series["r_10s"],
                    "r_30s": series["r_30s"],
                    "r_60s": series["r_60s"],
                    "y_1s": series["y_1s"],
                    "y_5s": series["y_5s"],
                    "y_10s": series["y_10s"],
                    "y_30s": series["y_30s"],
                    "y_60s": series["y_60s"],
                    "exec_1s": series["exec_1s"],
                    "exec_5s": series["exec_5s"],
                    "exec_10s": series["exec_10s"],
                    "exec_30s": series["exec_30s"],
                    "exec_60s": series["exec_60s"]
                })

                out_file = token_dir / f"features_{interval_ms}ms.csv"
                df.write_csv(out_file)
                total_files += 1
                total_rows += len(df)

    print(f"✅ Successfully generated {total_files} partitioned dataset files ({total_rows:,} total rows) in {output_path}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate synthetic LOB dataset")
    parser.add_argument("--output-dir", default="data/parquet", help="Output directory")
    parser.add_argument("--days", type=int, default=3, help="Number of trading days")
    parser.add_argument("--tokens", default="738561,1333,1594", help="Comma-separated tokens")
    parser.add_argument("--rows-per-day", type=int, default=3000, help="Base rows per day for 1000ms grid")
    args = parser.parse_args()

    token_list = [int(t.strip()) for t in args.tokens.split(",") if t.strip()]
    generate_multi_day_dataset(args.output_dir, args.days, token_list, args.rows_per_day)
