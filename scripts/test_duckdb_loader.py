import os
import shutil
import tempfile
from pathlib import Path
from duckdb_feature_loader import DuckDBFeatureLoader
import polars as pl

def test_duckdb_loader():
    temp_dir = tempfile.mkdtemp()
    try:
        # Create mock partitioned directory structure
        part_dir = Path(temp_dir) / "date=2026-08-29" / "instrument_token=738561"
        part_dir.mkdir(parents=True, exist_ok=True)
        csv_file = part_dir / "features_1000ms.csv"
        
        header = "grid_seq,grid_nanos,delta_nanos,instrument_token,symbol,snapshot_age_ms,best_bid,best_ask,mid_price,spread,rel_spread_bps,ltp,cum_volume,vwap,l1_obi,total_obi,w_obi_lin,w_obi_exp,microprice,micro_pressure,micro_pressure_bps,ml_microprice,l1_ofi,ml_ofi_uniform,ml_ofi_exp,trade_strength,buy_pressure,sell_pressure,r_1s,r_5s,r_10s,r_30s,r_60s,y_1s,y_5s,y_10s,y_30s,y_60s,exec_1s,exec_5s,exec_10s,exec_30s,exec_60s\n"
        row1 = "1,1000000000,1000000000,738561,RELIANCE,2.5,2499.5,2500.5,2500.0,1.0,4.0,2500.0,1000,2500.0,0.1,0.15,0.12,0.11,2500.1,0.1,0.4,2500.1,50.0,40.0,35.0,0.2,0.6,0.4,0.0005,0.001,0.0015,0.002,0.003,1,1,1,1,1,0.0001,0.0006,0.0011,0.0016,0.0026\n"
        row2 = "2,2000000000,1000000000,738561,RELIANCE,3.1,2500.5,2501.5,2501.0,1.0,4.0,2501.0,1050,2500.5,-0.1,-0.05,-0.08,-0.09,2500.9,-0.1,-0.4,2500.9,-30.0,-25.0,-20.0,-0.1,0.45,0.55,-0.0004,-0.0008,-0.0012,-0.0018,-0.0025,-1,-1,-1,-1,-1,-0.0008,-0.0012,-0.0016,-0.0022,-0.0029\n"
        
        with open(csv_file, "w") as f:
            f.write(header + row1 + row2)
            
        loader = DuckDBFeatureLoader(data_root=temp_dir)
        df = loader.query_features(trade_date="2026-08-29", instrument_token=738561, interval_ms=1000)
        
        assert len(df) == 2, f"Expected 2 rows, got {len(df)}"
        assert "mid_price" in df.columns
        assert df["mid_price"][0] == 2500.0
        assert df["mid_price"][1] == 2501.0
        
        stats = loader.compute_summary_statistics(interval_ms=1000)
        assert stats["total_rows"] == 2
        assert abs(stats["avg_mid_price"] - 2500.5) < 0.001
        
        print("✅ DuckDB & Polars Feature Pipeline successfully verified!")
    finally:
        shutil.rmtree(temp_dir)

if __name__ == "__main__":
    test_duckdb_loader()
