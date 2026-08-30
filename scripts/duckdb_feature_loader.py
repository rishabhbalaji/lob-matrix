import os
import duckdb
import polars as pl
from pathlib import Path
from typing import Optional, List

class DuckDBFeatureLoader:
    """
    High-speed columnar feature dataset loader using DuckDB and Polars.
    Optimized for multi-threaded scan of partitioned Parquet/CSV dataset trees.
    """
    def __init__(self, data_root: str = "data/parquet"):
        self.data_root = Path(data_root)
        self.con = duckdb.connect(database=":memory:")
        # Configure multi-threading
        self.con.execute("PRAGMA threads=4;")

    def query_features(self, 
                       trade_date: Optional[str] = None, 
                       instrument_token: Optional[int] = None, 
                       interval_ms: int = 1000) -> pl.DataFrame:
        """
        Queries features across partitioned directories and returns a zero-copy Polars DataFrame.
        """
        pattern = f"{self.data_root}/**/features_{interval_ms}ms.*"
        
        query = f"SELECT * FROM read_csv_auto('{pattern}', hive_partitioning=1)"
        clauses = []
        if trade_date:
            clauses.append(f"date = '{trade_date}'")
        if instrument_token:
            clauses.append(f"instrument_token = {instrument_token}")
            
        if clauses:
            query += " WHERE " + " AND ".join(clauses)
            
        arrow_table = self.con.execute(query).arrow()
        return pl.from_arrow(arrow_table)

    def compute_summary_statistics(self, interval_ms: int = 1000) -> dict:
        """
        Computes ultra-fast aggregated dataset statistics in C++ via DuckDB.
        """
        pattern = f"{self.data_root}/**/features_{interval_ms}ms.*"
        res = self.con.execute(f"""
            SELECT 
                COUNT(*) as total_rows,
                AVG(mid_price) as avg_mid_price,
                AVG(spread) as avg_spread,
                AVG(l1_obi) as avg_l1_obi,
                AVG(snapshot_age_ms) as avg_age_ms,
                AVG(trade_strength) as avg_trade_strength
            FROM read_csv_auto('{pattern}', hive_partitioning=1)
        """).fetchone()
        
        return {
            "total_rows": res[0],
            "avg_mid_price": res[1],
            "avg_spread": res[2],
            "avg_l1_obi": res[3],
            "avg_age_ms": res[4],
            "avg_trade_strength": res[5]
        }
