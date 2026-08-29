# Order Book Analysis: DOCX vs. Reality & Master Implementation Plan

---

## 📑 Section 1: Executive Comparison — Original DOCX vs. Engineering & Mathematical Reality

| Dimension | Original DOCX Specification Assumption | Ground Truth Reality & Production Fix |
|---|---|---|
| **Feed Type & Semantics** | Assumes an unfiltered, execution-by-execution trade log with unique `trade_id` and microsecond exchange sequencing. | **KiteTicker is a sampled/throttled Top-5 snapshot protocol**. No individual trade execution tickets (`trade_id`), no aggressor flags. Pushed at variable broker rates. |
| **Research Framing** | "High-frequency order-flow and trade-strength prediction of future price movements." | **"Empirical investigation into predictive information retained in broker-distributed Top-5 snapshots under temporal sampling and execution constraints."** |
| **Order Flow Imbalance (OFI)** | Assumes $\Delta Q_{\text{bid}}$ directly attributes order additions vs. cancellations vs. trade executions. | **Causal event breakdown is fundamentally unobservable with snapshots**. We implement a **Snapshot-Derived OFI Proxy** (Cont-Kukanov-Stoikov Multi-Level formulation) conditioning on price shifts. |
| **Timestamping & Clocks** | Assumes high-resolution timestamps from exchange to correlate sub-second trades. | **Kite exchange timestamps are truncated to integer seconds**. We implement a **Dual-Timestamp Architecture** using `client_arrival_time` (`System.nanoTime()`) for all causal ordering and grid indexing. |
| **System Architecture** | Proposes complex enterprise stack (Spring Boot + LMAX Disruptor + distributed clustering + heavy PostgreSQL tick tables). | **Single-Process Java 21 Engine**: Pinned state thread (zero lock contention), bounded MPSC queues, async WAL writer, consuming $< 300\text{ MB RAM}$. |
| **Storage Architecture** | Proposes saving high-frequency raw ticks directly into PostgreSQL tables. | **Immutable 40-Byte Binary WAL Files on disk $\rightarrow$ Automated batch Parquet conversion (ZSTD)** for high-speed columnar research in DuckDB/Pandas. PostgreSQL stores only metadata and dashboard state. |
| **Target Construction** | Fixed or unscaled volatility threshold for multi-horizon returns ($1\text{s} \dots 60\text{s}$). | **Horizon-Scaled Volatility Threshold**: $\sigma_\tau = \sigma_{\Delta t} \sqrt{\frac{\tau}{\Delta t}}$, ensuring 5-second targets are not evaluated against 15-minute unscaled noise. |
| **Data Validation** | Recommends a naive chronological 60% Train / 20% Val / 20% Test split. | **Walk-Forward Rolling Splits with Information Interval Overlap Purging**: Explicitly purging overlapping label windows ($I_k^{\text{label}} = [T_k, T_k + \tau]$) and feature lookback windows ($I_k^{\text{feature}} = [T_k - L, T_k]$). |
| **Multiple Testing** | Proposes testing dozens of feature/horizon/regime combinations without statistical correction. | **Benjamini-Hochberg False Discovery Rate (FDR $q < 0.05$)** and **Deflated Sharpe Ratio (DSR)** to eliminate data-snooping false positives across 4,000 parameter combinations. |
| **Transaction Costs** | General mention of slippage and spread. | **Explicit Versioned Indian Regulatory Cost Schedule**: Effective Oct 2024 revised STT (0.025% sell), NSE turnover (0.00297%), SEBI fees, GST, stamp duty $\approx 0.034\% - 0.048\%$. |

---

## ❓ Section 2: Authoritative Answers to the 15 Technical Questions

### Q1: Kite Feed Realities & Documented Boundaries
* **Kite Protocol (`Full` mode = 184 bytes)**: Provides Top-5 Bids/Asks (Price, Qty, Orders count), LTP, LTQ, cumulative volume, day OHLC, and integer-second timestamps.
* **Unobservable Boundaries**: No individual trade execution log (`trade_id`), no aggressor side tags, and no sub-second exchange timestamps. Update frequency is variable and must be measured empirically ($\Delta t_i = t_i^{\text{mono}} - t_{i-1}^{\text{mono}}$).

### Q2: Formal Resampling Operator
* **Definition**: Last-Observation-Carried-Forward (LOCF) strictly before or at grid point $T_k^{\text{mono}}$:
  $$\mathcal{B}^*(T_k) = \mathcal{B}_m \quad \text{where } m = \max \{ i \mid t_i^{\text{mono}} \le T_k^{\text{mono}} \}$$
* **Freshness Feature**: $\text{Age}(T_k) = T_k^{\text{mono}} - t_m^{\text{mono}}$ recorded as `snapshot_age_ms`. If $\text{Age}(T_k) > 5000\text{ms}$, mark state as stale.

### Q3: True OFI vs. Snapshot Proxy OFI
* In a snapshot feed, $\Delta Q$ is a net aggregation of additions, cancellations, executions, and price-level shifts. It cannot be causally decomposed.
* We implement pre-registered **Multi-Level Snapshot-Derived Proxy OFI** (`ML-OFI-Uniform` with $w=0.20$ and `ML-OFI-Exponential` with $\lambda=0.5$).

### Q4: Session State & Crossed Books Policy
* Explicit state machine: `PRE_OPEN_ORDER_ENTRY` (09:00-09:08), `PRE_OPEN_MATCHING` (09:08-09:15), `CONTINUOUS_TRADING` (09:15-15:30), `POST_CLOSE_AUCTION` (15:30-16:00).
* Crossed/Locked books are tagged (`STATE_CROSSED`, `STATE_LOCKED`, `STATE_EMPTY_SIDE`) and cross duration is tracked. Features are marked NaN during crossed states without discarding raw data.

### Q5: Dual-Timestamp Architecture
* **`epoch_recv_micros`** (`Instant.now()`): Used for database routing, session logs, and human timestamps.
* **`mono_recv_nanos`** (`System.nanoTime()`): Used exclusively for all in-process ordering, sub-second latency, grid resampling, and $T+\tau$ target indexing. Immune to NTP clock steps.

### Q6: Lean Single-Process Architecture
* One Java 21 process:
  1. Socket Ingestor Thread (attaches monotonic stamp + sequence counter).
  2. Bounded MPSC Queue (`capacity = 65,536`).
  3. Single Dedicated State & Feature Engine Thread (exclusive lock-free owner of in-memory order books).
  4. Async Binary WAL Writer Thread.
* Bounded queue policy: `DROP_OLDEST_UI_FRAME` on congestion; raw disk write backpressure triggers explicit telemetry (`INGESTION_OVERFLOW`, `RAW_FRAME_DROPPED`).

### Q7: Empirical Pilot Benchmarks
* Instrument and record empirical distributions ($p_1, p_{50}, p_{99}, \max$) for packet arrival gaps, decode latency, feature compute latency, JVM GC pauses (`-Xlog:gc*`), and write throughput.

### Q8: Deterministic Raw Storage Format (40-Byte Header)
* Fixed 40-byte binary header per WebSocket frame envelope: `magic_bytes` (4B: `0x4F424157`), `version` (2B), `connection_id` (2B), `global_capture_sequence` (8B), `mono_recv_nanos` (8B), `epoch_recv_micros` (8B), `payload_length` (4B), `payload_crc32` (4B), followed by $L$ raw bytes. Enables 100% bit-for-bit offline replay.

### Q9: Storage Lifecycle & Crash Recovery
* Live: Append-only uncompressed `.raw` log with `FileChannel.force(false)` flushes every 1,000ms.
* Post-Session (15:45 IST): Automated conversion to partitioned **Parquet** (ZSTD compressed) by date and instrument token. PostgreSQL stores only metadata and experiment logs.
* Crash Recovery: Scans CRC32 and frame lengths, truncates partial trailing frames to clean boundaries, and quarantines corrupted bytes.

### Q10: Three Distinct Forward Return Targets
1. **Informational Target ($R_{\text{mid}, \tau}$)**: $\ln(P_{\text{mid}}^*(T_k + \tau) / P_{\text{mid}}^*(T_k))$ — Primary research target for Goal A.
2. **Directional Target ($Y_\tau$)**: Tri-class label $\{-1, 0, +1\}$ using horizon-scaled volatility threshold $\theta_\tau = \max(0.5\sigma_\tau, \frac{\text{Spread}}{2P_{\text{mid}}})$.
3. **Executable P&L Target ($R_{\text{exec}, \tau}$)**: Spread-crossed return $(B_1^*(T_k+\tau) - A_1^*(T_k))/A_1^*(T_k) - \text{Fees}$ for trading feasibility.

### Q11: Multiple Hypothesis Testing & Data Snooping Defense
* Controlled via **Benjamini-Hochberg False Discovery Rate (FDR $q < 0.05$)** and **Deflated Sharpe Ratio (DSR)** across all tested parameter permutations.
* Strict separation: Exploratory feature tuning on Discovery Set; final validation locked on Confirmation Test Set.

### Q12: Information Interval Overlap Purging
* Purge sample $k$ if its label interval $[T_k, T_k + \tau]$ or feature interval $[T_k - L, T_k]$ overlaps with prohibited test intervals. Market close (15:30 to 09:15) provides natural session independence.

### Q13: Model Evaluation Framework
* Out-of-sample Spearman Rank IC with $t > 3.0$ ($p < 0.001$).
* Stationary Block Bootstrap 95% Confidence Intervals (Politis & Romano).
* Brier score calibration relative to empirical class prevalence.
* Incremental $\Delta \text{IC} > +0.015$ over Level-Weighted OBI baseline.

### Q14: Versioned Indian Regulatory Cost Schedule
* Versioned repository model (`CostModelRepository.get(date)`): Brokerage ₹20/order, STT 0.025% sell, NSE turnover 0.00297%, SEBI fee, GST 18%, Stamp Duty 0.003% ($\approx 0.034\% - 0.048\%$).
* Queue position and passive fill probabilities explicitly marked "unmodeled" due to Top-5 snapshot limitations.

### Q15: Single Brutal Research Hypothesis ($H_1$) & Falsification
* **Primary Hypothesis ($H_1$)**: *"Conditional on relative spread and volatility regime, snapshot-derived $W\text{-OBI}$ exhibits statistically significant out-of-sample rank association (Spearman Rank IC $> 0$, FDR $q < 0.05$) with 5-second forward mid-price log returns ($R_{\text{mid}, 5s}$) across liquid NSE equities."*
* **Falsification**: $|\text{IC}| < 0.02$, failure to beat autoregressive momentum baseline, or signal half-life decay $< 1.0\text{ second}$. A rigorous negative result is an academically successful outcome.

---

## 🚀 Section 3: Master Proposed Action Plan (Phase 1 to Phase 6)

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               MASTER IMPLEMENTATION ROADMAP                            │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 1: Core Foundation & Immutable Raw WAL Writer (Java 21 / Spring Boot)          │
│  • Project bootstrap with Maven, Lombok, Netty WebSocket Client                        │
│  • Immutable 40-byte binary WAL logger & crash-recovery verification                   │
│  • Mock Market Data Replay Feeder for zero-risk local development                      │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 2: In-Memory Order Book State & Microstructure Feature Engine                   │
│  • Zero-allocation mutable state manager with session state machine                    │
│  • Mathematical engine: W-OBI, Microprice, Multi-Level OFI, Trade Strength             │
│  • Fixed-Time Resampling Grid (LOCF) with snapshot age tracking                        │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 3: Batch Parquet Pipeline & PostgreSQL Metadata Store                           │
│  • Automated post-market converter: `.raw` WAL -> Partitioned Parquet (ZSTD)           │
│  • DuckDB / Apache Arrow integration for sub-second dataset queries                    │
│  • PostgreSQL metadata repository for sessions, cost models, and calibration logs      │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 4: Python Quantitative Research Lab & 2D Information Surface                    │
│  • The 2D Information Matrix experiment ($IC(\Delta t, \tau)$ across 25 configurations)│
│  • Non-linear ML training (LightGBM/XGBoost on CPU in < 45 seconds)                    │
│  • False Discovery Rate (FDR) controller, Block Bootstrap CIs, and DSR report          │
│  • Automated `model.onnx` export bridge                                                │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 5: Live Real-Time Dashboard (Java Spring Boot + WebSocket UI)                   │
│  • ONNX Runtime for Java (< 0.05 ms inference per tick)                                │
│  • Real-time Depth Ladder, Imbalance Speedometer, and Return Probability Gauges        │
│  • Non-blocking RingBuffer broadcast to browser                                        │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 6: Empirical Pilot Session & Final Scientific Research Report                   │
│  • 1-week pilot capture on 10 liquid NSE equities                                      │
│  • Formal empirical report answering the 10 research questions                         │
└────────────────────────────────────────────────────────────────────────────────────────┘
```
