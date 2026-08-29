# LobMatrix (`lob-matrix`): Market Microstructure & 2D Information Surface Platform
## Master Technical Specification, ELI5 Guide, DOCX Differences & Action Plan

---

## 📑 Table of Contents
1. [Executive Summary & Core Scientific Framing](#1-executive-summary--core-scientific-framing)
2. [Master ELI5 Guide (Concepts, Math & 24-Hour Loop)](#2-master-eli5-guide-concepts-math--24-hour-loop)
3. [Summary of Differences: Original DOCX vs. Engineering Reality](#3-summary-of-differences-original-docx-vs-engineering-reality)
4. [Authoritative Technical Answers to the 15 Core Questions](#4-authoritative-technical-answers-to-the-15-core-questions)
5. [The Central Science Experiment: 2D Information Surface](#5-the-central-science-experiment-2d-information-surface)
6. [Feed-Agnostic Canonical Market Architecture](#6-feed-agnostic-canonical-market-architecture)
7. [Historical Data Sourcing & Cold-Start Strategy (Past 90 Days)](#7-historical-data-sourcing--cold-start-strategy-past-90-days)
8. [Hardware & Machine Learning Feasibility on `rbkasus`](#8-hardware--machine-learning-feasibility-on-rbkasus)
9. [Master Proposed Action Plan (Phase 1 to Phase 6)](#9-master-proposed-action-plan-phase-1-to-phase-6)
10. [Granular Progress Tracker & Git Commit Protocol (`MXPYSZ`)](#10-granular-progress-tracker--git-commit-protocol-mxpysz)

---

## 1. Executive Summary & Core Scientific Framing

### The Honest Research Objective:
> **"An Empirical Investigation into the Predictive Information Retained in Broker-Distributed Top-5 Order-Book Snapshots under Temporal Sampling, Latency, and Execution Constraints."**

Rather than pretending we have a direct microsecond exchange feed, this platform models the true transmission pipeline:
$$\text{Exchange Order Flow} \longrightarrow \text{Broker Gateway Sampling} \longrightarrow \text{Observed Top-5 Snapshot} \longrightarrow \text{Feature Space} \longrightarrow \text{Predictive Information } I(\text{Features}; R_{t+\tau})$$

It investigates whether high-frequency order book dynamics (Order Book Imbalance, Multi-Level Proxy OFI, Microprice Pressure, and Trade Strength) contain statistically and economically significant predictive power over short-term price movements ($1\text{s}, 5\text{s}, 10\text{s}, 30\text{s}, 60\text{s}$).

---

## 2. Master ELI5 Guide (Concepts, Math & 24-Hour Loop)

### 🏬 The Apple Auction Metaphor
Imagine a busy town square where buyers and sellers stand in two queues:

```
          BUYERS (Bids)                        SELLERS (Asks)
  "I want to pay at most..."             "I want to sell for at least..."

   [Level 1]  ₹100  (10 apples)    vs    [Level 1]  ₹101  (5 apples)
   [Level 2]  ₹99   (20 apples)          [Level 2]  ₹102  (15 apples)
   [Level 3]  ₹98   (50 apples)          [Level 3]  ₹103  (30 apples)
   [Level 4]  ₹97   (100 apples)         [Level 4]  ₹104  (40 apples)
   [Level 5]  ₹96   (200 apples)         [Level 5]  ₹105  (80 apples)
```

* **Best Bid ($B_1$)**: Highest price a buyer will pay (₹100).
* **Best Ask ($A_1$)**: Lowest price a seller will accept (₹101).
* **Mid-Price ($P_{\text{mid}}$)**: $\frac{100 + 101}{2} = ₹100.50$.
* **Spread**: $101 - 100 = ₹1.00$.

### 🧮 Core Microstructure Features Explained:
1. **Order Book Imbalance (OBI)**: A tug-of-war counting total buyers vs. total sellers:
   $$\text{OBI} = \frac{\text{Bid Depth} - \text{Ask Depth}}{\text{Bid Depth} + \text{Ask Depth}} \in [-1.0, +1.0]$$
2. **Level-Weighted Imbalance (W-OBI)**: Giving $100\%$ weight to people at the front counter (Level 1) and decaying weights ($0.8, 0.6, 0.4, 0.2$) to people in the back parking lot (Level 5).
3. **Microprice & Microprice Pressure**: The "gravity magnet" price pulled closer to whichever side has more queued orders. Pressure $= \text{Microprice} - P_{\text{mid}}$.
4. **Trade Strength**: Measures aggressive market orders bursting in: $\frac{\text{Buy Vol} - \text{Sell Vol}}{\text{Buy Vol} + \text{Sell Vol}} \in [-1.0, +1.0]$.
5. **Snapshot-Based OFI (Order Flow Imbalance)**: Measures changes between consecutive snapshots to catch "ghost cancellations" (people placing fake orders and pulling them before trading).
6. **Snapshot Age (`snapshot_age_ms`)**: Measures whether a book state is fresh ($5\text{ms}$) or stale ($4.8\text{s}$).

### 🔄 The Daily 24-Hour Loop (Java ➡️ Python ➡️ Java):
```
   [09:15 AM - 03:30 PM]              [03:45 PM - 04:00 PM]              [NEXT MORNING 09:15 AM]
 ┌──────────────────────┐          ┌──────────────────────┐          ┌──────────────────────┐
 │  STEP 1: JAVA        │          │  STEP 2: PYTHON      │          │  STEP 3: JAVA        │
 │  The High-Speed      │          │  The Master Chef     │          │  The Super Robot     │
 │  Kitchen Worker 🤖   │          │  in the Lab 👨‍🍳     │          │  Executes the Plan ⚡ │
 ├──────────────────────┤          ├──────────────────────┤          ├──────────────────────┤
 │ • Catches live ticks │          │ • Reads Parquet data │          │ • Loads `model.onnx` │
 │ • Calculates features│ ───────► │ • Discovers patterns │ ───────► │ • Predicts in 0.05ms │
 │ • Saves raw WAL &    │  Clean   │ • Trains AI (XGBoost)│  Magic   │ • Shows live 68%     │
 │   Parquet files      │  Parquet │ • Exports `model.onnx│  Card    │   gauge on Dashboard │
 └──────────────────────┘          └──────────────────────┘          └──────────────────────┘
            ▲                                                                   │
            │                                                                   │
            └────────────────────── The Cycle Repeats ──────────────────────────┘
```

---

## 3. Summary of Differences: Original DOCX vs. Engineering Reality

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

## 4. Authoritative Technical Answers to the 15 Core Questions

### Q1: Kite Feed Realities & Documented Boundaries
* **Kite Protocol (`Full` mode = 184 bytes)**: Documented binary payload contains Token ID, LTP, LTQ, ATP, cumulative volume, total buy/sell depth, day OHLC, last trade time (seconds), exchange timestamp (seconds), and 120 bytes of Top-5 Market Depth (Price, Qty, Orders count per level).
* **Unobservable Boundaries**: No individual trade execution tickets (`trade_id`), no aggressor tags (`BUY`/`SELL`), and no sub-second exchange timestamps. Update frequency is variable and must be empirically measured ($\Delta t_i = t_i^{\text{mono}} - t_{i-1}^{\text{mono}}$).

### Q2: Formal Resampling Operator
* **Operator Definition**: Last-Observation-Carried-Forward (LOCF) strictly before or at grid point $T_k^{\text{mono}}$:
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

## 5. The Central Science Experiment: 2D Information Surface

We will test multiple sampling frequencies ($\Delta t$) against multiple forward horizons ($\tau$) to construct the empirical Information Surface Matrix:

$$\text{Information Surface } \mathcal{M} = \left[ \text{Rank IC}(\Delta t_i, \tau_j) \right]$$

$$\text{for } \Delta t \in \{100\text{ms}, 250\text{ms}, 500\text{ms}, 1000\text{ms}, 2000\text{ms}\} \quad \text{and} \quad \tau \in \{1\text{s}, 5\text{s}, 10\text{s}, 30\text{s}, 60\text{s}\}$$

```
                           FORECAST HORIZON (tau)
SAMPLING (Delta t)     1s       5s       10s      30s      60s
──────────────────────────────────────────────────────────────
100 ms               IC(1,1)  IC(1,2)  IC(1,3)  IC(1,4)  IC(1,5)
250 ms               IC(2,1)  IC(2,2)  IC(2,3)  IC(2,4)  IC(2,5)
500 ms               IC(3,1)  IC(3,2)  IC(3,3)  IC(3,4)  IC(3,5)
1000 ms              IC(4,1)  IC(4,2)  IC(4,3)  IC(4,4)  IC(4,5)
2000 ms              IC(5,1)  IC(5,2)  IC(5,3)  IC(5,4)  IC(5,5)
```
*Core Discovery Goal*: Identify the exact temporal resolution where broker market depth loses predictive alpha.

---

## 6. Feed-Agnostic Canonical Market Architecture

Rather than coupling our calculation engine to Zerodha's specific 184-byte binary layout, the platform implements a **Feed-Agnostic Adapter Pattern**. This allows ingestion from any broker or institutional data vendor with zero changes to downstream feature or ML pipelines:

```
                               NSE Market
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
     Zerodha Kite Adapter    Upstox Adapter        Dhan Adapter
     (Top-5 Binary Frame)  (Protobuf Stream)   (Top-20 Depth JSON/Bin)
              │                    │                    │
              └────────────────────┼────────────────────┘
                                   │
                                   ▼
                   CanonicalMarketSnapshot (Java 21 Record)
                   • sourceId: "ZERODHA" | "DHAN" | "UPSTOX"
                   • depthLevels: 5 | 20 | 200
                   • normalized bid/ask arrays & timestamps
                                   │
                                   ▼
                       Canonical Raw Binary WAL
                                   │
                                   ▼
             Multi-Level Feature Engine (Handles N Levels)
                                   │
                                   ▼
                   Cross-Broker Depth Comparison
             (Does Top-20 depth contain more alpha than Top-5?)
```

### The Java 21 Canonical Data Contract (`CanonicalMarketSnapshot`):
```java
public record CanonicalMarketSnapshot(
    String sourceId,            // "ZERODHA", "DHAN", "UPSTOX", "TRUEDATA"
    long instrumentToken,       // Unified internal instrument identifier
    String symbol,              // "RELIANCE", "NIFTY50"
    long clientArrivalNanos,    // System.nanoTime()
    long clientArrivalMicros,   // Instant.now()
    long exchangeEpochSecs,     // Exchange timestamp
    double ltp,                 // Last Traded Price
    long ltq,                   // Last Traded Quantity
    long cumulativeVolume,      // Total day volume
    double dayVwap,             // Average Traded Price
    int depthLevels,            // 5, 20, or 200
    double[] bidPrices,         // Array of size depthLevels
    long[] bidQuantities,       // Array of size depthLevels
    int[] bidOrders,            // Array of size depthLevels
    double[] askPrices,         // Array of size depthLevels
    long[] askQuantities,       // Array of size depthLevels
    int[] askOrders             // Array of size depthLevels
) {}
```

### 🔬 The Cross-Broker Depth Comparison Experiment:
By supporting **Dhan (Top-20 depth)** alongside **Zerodha (Top-5 depth)**, the research answers:
> **"Does 20-level market depth provide statistically significant incremental predictive alpha over 5-level market depth under broker WebSocket sampling?"**

---

## 7. Historical Data Sourcing & Cold-Start Strategy (Past 90 Days)

### Will 90 Days of Past Data Help?
**YES, immensely.** With 90 days of historical Level-2 order book depth:
1. **Zero Cold Start on Day 1**: You start Day 1 with a fully trained, calibrated `model.onnx` ready to predict at 09:15 AM.
2. **Pre-Flight Validation**: Walk-forward cross-validation, feature weights, and the $IC(\Delta t, \tau)$ surface can be verified offline before live streaming.

### Where to Find Historical Level-2 / Level-3 Data:

| Source | What It Actually Provides | Cost | Verdict for Our Project |
|---|---|---|---|
| **Broker REST APIs** *(Kite, Upstox REST)* | ❌ **ONLY OHLCV Candles** (1-min, 5-min, daily bars). **Brokers do NOT store or sell historical Top-5/Top-20 depth.** | ₹2,000 / mo | ❌ Cannot be used for microstructure (no depth levels). |
| **Authorized NSE Data Vendors** *(TrueData, GlobalDataFeeds)* | ✅ **Historical 1-second / tick-level Level-2 (Top-5 depth) datasets** for NSE equities in CSV/Parquet. | ₹1,500 – ₹4,000 / mo of data | 🏆 **Best option for immediate 90-day cold-start data.** |
| **NSE Official Data Store** *(NSE InfSys)* | ✅ Official Level-2 / Level-3 tick-by-tick historical logs. | ₹10,000+ / mo | 🏢 Expensive institutional pricing. |
| **Self-Hosted Ingestion (Your Engine)** | ✅ Captures 100% accurate, bit-for-bit raw WAL & Parquet files daily. | **₹0.00 (Free)** | 🚀 **Build your own proprietary 90-day archive over time!** |

---

## 8. Hardware & Machine Learning Feasibility on `rbkasus`

### Machine Specifications:
* **Host**: `rbkasus` (Ubuntu 24.04 LTS x86_64)
* **CPU**: Intel Core i5-8250U (4 Cores / 8 Threads @ up to 3.4 GHz)
* **RAM**: 24 GB Total (**~21.5 GB Available**)
* **Background Tasks**: Minecraft server, Jellyfin media server, *arr stack.

### Why ML Training Will NOT Overwhelm or Lag This Machine:
1. **Tabular Numeric Models $\ne$ Heavy GPUs**:
   * LightGBM, XGBoost `hist`, and Logistic Regression are tabular models executing vectorized CPU instructions (AVX2).
   * **Zero GPU needed or used**.
2. **Memory Footprint**:
   * 1 full month of 10 liquid stocks (1-second resampled) = **4.5 million rows** $\approx$ **380 MB RAM** (less than 2% of your 21.5 GB free RAM).
3. **Training Speed**:
   * Training 100 trees in LightGBM takes **~25 to 40 seconds on your CPU**.
   * Run once a day at 15:45 IST (post-market close) or weekly on Sunday night via cron.

---

## 9. Master Proposed Action Plan (Phase 1 to Phase 6)

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               MASTER IMPLEMENTATION ROADMAP                            │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 1: Core Foundation & Immutable Raw WAL Writer (Java 21 / Spring Boot)           │
│  • Feed-Agnostic Adapter interface (`MarketFeedAdapter`) & Zerodha/Dhan/Mock decoders  │
│  • Immutable 40-byte binary WAL logger (`CanonicalMarketSnapshot`) & crash recovery   │
│  • Mock Market Data Replay Feeder for zero-risk local development                      │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 2: In-Memory Order Book State & Microstructure Feature Engine                   │
│  • Zero-allocation mutable state manager with session state machine                    │
│  • Mathematical engine: W-OBI, Microprice, Multi-Level OFI (Top-5 & Top-20), Strength │
│  • Fixed-Time Resampling Grid (LOCF) with snapshot age tracking                        │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 3: Batch Parquet Pipeline & PostgreSQL Metadata Store                           │
│  • Automated post-market converter: `.raw` WAL -> Partitioned Parquet (ZSTD)           │
│  • DuckDB / Apache Arrow integration for sub-second dataset queries                    │
│  • PostgreSQL metadata repository for sessions, cost models, and calibration logs      │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 4: Python Quantitative Research Lab & 2D Information Surface                    │
│  • The 2D Information Matrix experiment ($IC(\Delta t, \tau)$ across 25 configurations)│
│  • Top-5 vs. Top-20 cross-depth comparative alpha research                             │
│  • Non-linear ML training (LightGBM/XGBoost on CPU in < 45 seconds)                    │
│  • False Discovery Rate (FDR) controller, Block Bootstrap CIs, and DSR report          │
│  • Automated `model.onnx` export bridge                                                │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 5: Live Real-Time Dashboard (Java Spring Boot + WebSocket UI)                   │
│  • ONNX Runtime for Java (< 0.05 ms inference per tick)                                │
│  • Real-time Depth Ladder (5/20 levels), Imbalance Speedometer, & Probability Gauges   │
│  • Non-blocking RingBuffer broadcast to browser                                        │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  PHASE 6: Empirical Pilot Session & Final Scientific Research Report                   │
│  • 1-week pilot capture on 10 liquid NSE equities                                      │
│  • Formal empirical report answering the 10 research questions                         │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 10. Granular Progress Tracker & Git Commit Protocol (`MXPYSZ`)

All implementation tasks and Git commits follow the strict identifier protocol:
$$\mathbf{M_X P_Y S_Z: \text{ <Descriptive Action & Verification Output>}}$$
*(where $M_X = \text{Milestone } X, \; P_Y = \text{Phase } Y, \; S_Z = \text{Stage } Z$).*

```
Structure Overview:
• 6 Milestones (M1 to M6)
• 18 Phases (M1P1 to M6P3)
• 54 Granular Stages (M1P1S1 to M6P3S2)
```

---

### 🏛️ Milestone 1: Feed-Agnostic Core Ingestion Engine & Immutable Binary WAL

#### Phase 1 (M1P1): Project Bootstrap & Canonical Data Contract
* `M1P1S1`: **Bootstrap Java 21 Spring Boot Project & Base Dependencies**
  * *Objective*: Initialize Spring Boot 3.3.x with Java 21, Maven, Lombok, Netty, Jackson, and JUnit 5.
  * *Verification*: `./mvnw clean compile` succeeds with zero warnings.
* `M1P1S2`: **Define Immutable `CanonicalMarketSnapshot` Record & Session State Enums**
  * *Objective*: Create Java 21 unified record for market depth ($N$-levels), trade prices, quantities, and dual timestamps.
  * *Verification*: Unit tests verify immutability, memory compactness, and array deep-cloning guards.
* `M1P1S3`: **Define `MarketFeedAdapter` SPI (Service Provider Interface)**
  * *Objective*: Create pluggable adapter contract: `connect()`, `disconnect()`, `subscribe(tokens)`, `onPacket(Consumer<CanonicalMarketSnapshot>)`.
  * *Verification*: Interface compiles with mock lifecycle listener assertions.

#### Phase 2 (M1P2): Multi-Broker Decoders & Mock Feeder
* `M1P2S1`: **Implement `ZerodhaKiteBinaryDecoder` (184-Byte Full Packet Parser)**
  * *Objective*: Parse Kite binary frames, extract Top-5 depth, LTP, LTQ, ATP, cumulative volume, and assign `mono_recv_nanos`.
  * *Verification*: Unit test passes against raw 184-byte captured Kite binary test fixtures.
* `M1P2S2`: **Implement `DhanMarketDepthDecoder` (Top-20 Depth Parser)**
  * *Objective*: Parse Dhan WebSocket packets into 20-level `CanonicalMarketSnapshot` instances.
  * *Verification*: Unit test passes against simulated 20-level Dhan JSON/binary fixtures.
* `M1P2S3`: **Implement `MockMarketReplayFeeder` (Deterministic Offline Generator)**
  * *Objective*: Generate reproducible simulated market depth, trades, and gaps for zero-risk local development.
  * *Verification*: Emits 1,000 deterministic canonical ticks/sec to in-memory listeners.

#### Phase 3 (M1P3): Immutable 40-Byte Binary WAL & Crash Recovery
* `M1P3S1`: **Implement 40-Byte Header Binary WAL Serializer & CRC-32 Calculator**
  * *Objective*: Encode `magic_bytes` (`0x4F424157`), `global_capture_sequence`, `mono_recv_nanos`, `epoch_recv_micros`, CRC-32, and raw payload.
  * *Verification*: Binary round-trip test confirms byte-for-byte fidelity ($40 + L$ bytes).
* `M1P3S2`: **Implement `AsyncBinaryWALWriter` with Buffered `FileChannel` Disk Flush**
  * *Objective*: Asynchronously write binary envelopes to disk with 1,000ms `force(false)` durability syncs.
  * *Verification*: Benchmark confirms $< 5\ \mu\text{s}$ queue handoff latency; writes 50k envelopes with zero data corruption.
* `M1P3S3`: **Implement `WALCrashRecoveryEngine` (CRC-32 Validation & Tail Truncation)**
  * *Objective*: Scan `.raw` files on boot, detect mid-write truncated frames, truncate at clean boundary, and isolate corrupted bytes.
  * *Verification*: Test injects partial bytes into `.raw` file; recovery engine detects corruption and restores valid prefix cleanly.

---

### ⚡ Milestone 2: In-Memory Order Book State & Microstructure Feature Engine

#### Phase 1 (M2P1): Lock-Free State Ownership & Session State Machine
* `M2P1S1`: **Implement `OrderBookStateManager` (Pinned Single-Thread State Owner)**
  * *Objective*: Exclusive single-thread owner of `Map<Long, OrderBookState>` (zero mutexes, zero lock contention).
  * *Verification*: Thread-confinement tests verify state updates execute on a dedicated core with zero race conditions.
* `M2P1S2`: **Implement `SessionPhaseStateMachine` (NSE Trading Phases)**
  * *Objective*: State machine classifying `PRE_OPEN_ORDER_ENTRY` (09:00), `PRE_OPEN_MATCHING` (09:08), `CONTINUOUS_TRADING` (09:15), and `POST_CLOSE` (15:30).
  * *Verification*: Time-series transitions verified across synthetic 09:00–16:00 clock timestamps.
* `M2P1S3`: **Implement Crossed/Locked Book Classifier & `cross_duration_ms` Tracker**
  * *Objective*: Tag `STATE_CROSSED` ($\text{Bid}_1 > \text{Ask}_1$), `STATE_LOCKED` ($\text{Bid}_1 = \text{Ask}_1$), and `STATE_EMPTY_SIDE`.
  * *Verification*: Unit tests verify crossed conditions set ML features to NaN and track duration in milliseconds.

#### Phase 2 (M2P2): Microstructure Mathematics Engine
* `M2P2S1`: **Implement `OrderBookImbalanceCalculator` (Standard OBI & Level-Weighted W-OBI)**
  * *Objective*: Calculate raw OBI and decaying weighted W-OBI ($w = [1.0, 0.8, 0.6, 0.4, 0.2]$) for Top-5 and Top-20 depth.
  * *Verification*: Mathematical test verifies OBI strictly bounded in $[-1.0, +1.0]$.
* `M2P2S2`: **Implement `MicropriceCalculator` (Microprice & Microprice Pressure)**
  * *Objective*: Calculate volume-weighted microprice and pressure $\text{Microprice} - P_{\text{mid}}$.
  * *Verification*: Test verifies microprice shifts toward opposite side with higher volume.
* `M2P2S3`: **Implement `TradeStrengthClassifier` (Lee-Ready Rule & Intensity Windows)**
  * *Objective*: Classify buyer vs. seller aggressor volume and compute trade strength and volume intensity over rolling 1s, 5s, 10s, 30s windows.
  * *Verification*: Test validates trade volume attribution and rolling decay buffers.
* `M2P2S4`: **Implement `MultiLevelOFICalculator` (Cont-Kukanov-Stoikov Multi-Level OFI)**
  * *Objective*: Implement pre-registered `ML-OFI-Uniform` ($w=0.20$) and `ML-OFI-Exponential` ($\lambda=0.5$).
  * *Verification*: Unit test validates price level shifts do not trigger false cancellation spikes.

#### Phase 3 (M2P3): Deterministic Fixed-Time Resampling Grid
* `M2P3S1`: **Implement `FixedTimeResamplingEngine` (LOCF Operator strictly for $t_i \le T_k$)**
  * *Objective*: Resample continuous tick arrivals onto discrete clock grid $T_k^{\text{mono}} = t_0^{\text{mono}} + k\Delta t$.
  * *Verification*: Test asserts zero future observations ($t_i > T_k$) are ever included in grid state $T_k$.
* `M2P3S2`: **Implement `SnapshotAgeTracker` (`snapshot_age_ms` Calculation & Staleness Drop)**
  * *Objective*: Calculate age $T_k^{\text{mono}} - t_m^{\text{mono}}$; drop snapshots older than $\tau_{\text{stale}} = 5000\text{ms}$.
  * *Verification*: Stale gaps $> 5\text{s}$ successfully marked `STALE_DROPPED`.
* `M2P3S3`: **Implement Multi-Grid Clock Dispatcher ($\Delta t \in \{100\text{ms}, 250\text{ms}, 500\text{ms}, 1\text{s}, 2\text{s}\}$)**
  * *Objective*: Simultaneously evaluate 5 sampling frequencies for the 2D Information Surface experiment.
  * *Verification*: Dispatches synchronized resampled feature records across all 5 discrete grids.

---

### 📦 Milestone 3: Parquet Pipeline, Forward Targets & PostgreSQL Metadata Store

#### Phase 1 (M3P1): Zero-Lookahead Forward Target Labeler
* `M3P1S1`: **Implement `ForwardReturnTargetEngine` ($\tau \in \{1\text{s}, 5\text{s}, 10\text{s}, 30\text{s}, 60\text{s}\}$)**
  * *Objective*: Calculate informational mid-price log returns $R_{\text{mid}, \tau}(T_k) = \ln(P_{\text{mid}}^*(T_k + \tau) / P_{\text{mid}}^*(T_k))$.
  * *Verification*: Asserts return calculations use strictly causal forward indices with LOCF marks.
* `M3P1S2`: **Implement Horizon-Scaled Volatility Threshold $\theta_\tau$**
  * *Objective*: Calculate $\theta_\tau = \max(0.5\sigma_\tau, \frac{\text{Spread}}{2P_{\text{mid}}})$ with $\sigma_\tau = \sigma_{\Delta t}\sqrt{\tau / \Delta t}$.
  * *Verification*: Test proves threshold scales correctly with $\sqrt{\tau}$.
* `M3P1S3`: **Implement Executable Spread-Crossed P&L Calculator with Versioned Cost Model**
  * *Objective*: Compute net returns $R_{\text{exec}}$ deducting versioned Indian statutory fees (`CostModelRepository`).
  * *Verification*: Asserts half-spread and statutory taxes (STT, turnover, GST) are deducted accurately.

#### Phase 2 (M3P2): Automated Post-Market Parquet Exporter
* `M3P2S1`: **Build Batch WAL-to-Parquet Converter using Apache Arrow / Parquet Java (ZSTD)**
  * *Objective*: Read daily `.raw` WAL files at 15:45 IST and stream into columnar Parquet files.
  * *Verification*: Converts 1,000,000 raw frames into ZSTD-compressed Parquet in $< 10\text{ seconds}$.
* `M3P2S2`: **Partition Parquet Files by Date and Instrument Token**
  * *Objective*: Store at `/data/parquet/date=YYYY-MM-DD/instrument_token=XXXXX/features_1s.parquet`.
  * *Verification*: Directory structure verified with readable Parquet metadata schema.
* `M3P2S3`: **Build DuckDB / Apache Arrow High-Speed Query Integration**
  * *Objective*: Provide sub-second SQL / Python DataFrame query interface over 100 GB Parquet datasets.
  * *Verification*: Benchmark queries 5,000,000 rows in DuckDB in $< 150\text{ ms}$.

#### Phase 3 (M3P3): PostgreSQL Metadata & Session Repository
* `M3P3S1`: **Setup PostgreSQL Schema for `session_metadata`, `cost_models`, & `experiment_runs`**
  * *Objective*: Create relational tables for session telemetry, cost schedules, and backtest results.
  * *Verification*: Flyway migration executes cleanly against local PostgreSQL.
* `M3P3S2`: **Implement Spring Data JPA Repositories & Daily Session Finalizer Service**
  * *Objective*: Automate session completion, log packet statistics, and persist experiment metadata.
  * *Verification*: Automated test verifies session finalizer executes at simulated 15:45 IST trigger.

---

### 🐍 Milestone 4: Python Quantitative Research Lab & 2D Information Surface

#### Phase 1 (M4P1): 2D Information Surface Matrix & Correlation Analytics
* `M4P1S1`: **Implement `InformationSurfaceAnalyzer` ($IC(\Delta t, \tau)$ 25-Cell Matrix)**
  * *Objective*: Compute Spearman Rank IC across all 5 sampling grids $\times$ 5 horizons.
  * *Verification*: Generates $5 \times 5$ heatmap matrix with Student's $t$-statistic and $p$-values.
* `M4P1S2`: **Implement Signal Half-Life Decay Curve Plotter & Pearson/Spearman Matrices**
  * *Objective*: Plot empirical signal decay curves from $1\text{s}$ to $60\text{s}$ and feature cross-correlation heatmaps.
  * *Verification*: Script outputs publication-grade Matplotlib/Seaborn vector charts.
* `M4P1S3`: **Implement Top-5 vs. Top-20 Cross-Broker Depth Comparison Analysis**
  * *Objective*: Empirically test $\text{Rank IC}(\text{Top-5})$ vs. $\text{Rank IC}(\text{Top-20})$ on identical timestamps.
  * *Verification*: Outputs comparative delta metrics ($\Delta \text{IC}$) and statistical significance tests.

#### Phase 2 (M4P2): Machine Learning & Interval-Overlap Purged Cross-Validation
* `M4P2S1`: **Implement Information Interval Overlap Purging & Embargo Walk-Forward Validator**
  * *Objective*: Purge training samples overlapping test intervals $[T_k - L, T_k]$ and $[T_k, T_k + \tau]$.
  * *Verification*: Asserts zero information overlap between training folds and out-of-sample evaluation folds.
* `M4P2S2`: **Train LightGBM / XGBoost `hist` Multi-Factor Classifiers (CPU Multi-Threaded $< 45\text{s}$)**
  * *Objective*: Train non-linear decision tree models predicting directional return probabilities.
  * *Verification*: Training on 4.5 million rows completes in $< 45\text{s}$ on Intel i5 CPU with zero GPU.
* `M4P2S3`: **Implement Benjamini-Hochberg FDR Controller ($q < 0.05$) & Deflated Sharpe Ratio (DSR)**
  * *Objective*: Apply multiple testing corrections across all tested permutations and compute DSR.
  * *Verification*: FDR filter successfully identifies and rejects unadjusted false positive discoveries.

#### Phase 3 (M4P3): Model Serialization & ONNX Export Bridge
* `M4P3S1`: **Export LightGBM/XGBoost Models to `model.onnx` via `onnxmltools` / `skl2onnx`**
  * *Objective*: Serialize trained tree ensemble into standard ONNX binary format.
  * *Verification*: Model exported as `model.onnx` ($< 5\text{ MB}$ file size).
* `M4P3S2`: **Generate Feature Normalization Scaler Config & Versioned Model Metadata JSON**
  * *Objective*: Output `scaler_params.json` and `model_metadata.json` with pre-registered feature ordering.
  * *Verification*: JSON validation test confirms schema matches Java parser contract.

---

### 🖥️ Milestone 5: Real-Time Dashboard UI & Live ONNX Inference Engine

#### Phase 1 (M5P1): Live In-Process ONNX Inference
* `M5P1S1`: **Integrate Microsoft ONNX Runtime for Java (`com.microsoft.onnxruntime`)**
  * *Objective*: Load `model.onnx` into JVM heap and initialize high-speed `OrtSession`.
  * *Verification*: Java unit test executes sample tensor inference in $< 0.05\text{ ms}$ (50 microseconds).
* `M5P1S2`: **Implement Real-Time Tick Inference Evaluator**
  * *Objective*: Pass live computed features $[W\text{-OBI}, \text{MicroPressure}, \text{OFI}, \text{Strength}, \text{Spread}]$ into ONNX model.
  * *Verification*: Evaluates 20,000 live inferences/sec with zero memory leaks.
* `M5P1S3`: **Implement Automatic Fallback to Baseline Formula Mode on Missing Model**
  * *Objective*: If `model.onnx` is not present (Day 1), fallback to deterministic Baseline Strength Score without error.
  * *Verification*: Cold-start test verifies clean startup with status flag `MODE_BASELINE_ACTIVE`.

#### Phase 2 (M5P2): Low-Latency WebSocket Streaming Gateway
* `M5P2S1`: **Build Spring WebSocket `/ws/orderbook` Live Broadcasting Gateway**
  * *Objective*: Stream JSON/binary UI state updates to connected browser clients.
  * *Verification*: WebSocket client connects and receives live book states at 10 Hz refresh rate.
* `M5P2S2`: **Implement Non-Blocking RingBuffer Dispatcher (`DROP_OLDEST_UI_FRAME` Policy)**
  * *Objective*: Isolate UI socket slow-consumers from blocking the core market compute thread.
  * *Verification*: Slow consumer test proves core ingestion maintains $< 10\ \mu\text{s}$ latency during UI socket lag.

#### Phase 3 (M5P3): Interactive Web Dashboard Interface
* `M5P3S1`: **Build Live Level-2 / Level-20 Depth Ladder (Visual Green/Red Bid-Ask Bars)**
  * *Objective*: Render responsive visual DOM ladder with real-time volume bars.
  * *Verification*: UI renders 20 depth rows updating smoothly with zero browser memory bloat.
* `M5P3S2`: **Build Real-Time Imbalance Speedometer & Trade Strength Gauge**
  * *Objective*: Display dynamic SVG/Canvas meters swinging between $-100\%$ and $+100\%$.
  * *Verification*: Meters smoothly transition and reflect live incoming feature values.
* `M5P3S3`: **Build Live Price vs. Imbalance Chart & Return Probability Forecast Cards**
  * *Objective*: Plot dual-axis rolling price/imbalance chart and display live *"5s UP Probability: 68.4%"* cards.
  * *Verification*: Visual dashboard verified live on Chromium browser.

---

### 📊 Milestone 6: Pilot Deployment, Live Benchmarking & Final Empirical Report

#### Phase 1 (M6P1): Live Deployment & Systemd Automation on `rbkasus`
* `M6P1S1`: **Configure `systemd` Service (`Restart=always`) & Linux Chrony Slew Mode**
  * *Objective*: Ensure service auto-starts on laptop boot and NTP clock slew mode is enforced.
  * *Verification*: `systemctl status orderbook-engine` shows active (running) across simulated reboot.
* `M6P1S2`: **Connect to Live Zerodha Kite / Dhan WebSocket during 09:15-15:30 IST Market Hours**
  * *Objective*: Live ingestion across 10 liquid NSE equities (e.g. Reliance, HDFC Bank, Infosys, ICICI Bank, TCS).
  * *Verification*: Ingests full trading session with zero unhandled exceptions.
* `M6P1S3`: **Measure Empirical Telemetry ($\Delta t_i$, Decode Latency, Compute Latency, GC Pauses)**
  * *Objective*: Record actual empirical distributions ($p_1, p_{50}, p_{99}, \max$) for the research paper.
  * *Verification*: Outputs telemetry log table and verifies GC pauses remain $< 5\text{ ms}$.

#### Phase 2 (M6P2): Automated 24-Hour Loop Verification
* `M6P2S1`: **Verify Post-Market 15:45 IST Parquet Export Pipeline**
  * *Objective*: Ensure `.raw` WAL automatically converts to clean Parquet files at 15:45 IST.
  * *Verification*: Automated cron verifies partitioned Parquet files created with valid checksums.
* `M6P2S2`: **Verify Automated Python Model Retraining & `model.onnx` Refresh**
  * *Objective*: Trigger Python training script, verify model converges in $< 45\text{s}$, and exports `model.onnx`.
  * *Verification*: New `model.onnx` artifact created with updated timestamp.
* `M6P2S3`: **Verify Day-2 Automatic Model Reload & Real-Time Probability Activation**
  * *Objective*: Java engine detects updated `model.onnx` on next morning startup and unlocks full AI probability mode.
  * *Verification*: Status transitions from `MODE_BASELINE_ACTIVE` $\rightarrow$ `MODE_AI_PREDICTIVE_ACTIVE`.

#### Phase 3 (M6P3): Final Empirical Science Report
* `M6P3S1`: **Execute 1-Week Pilot Capture Across 10 Liquid NSE Equities**
  * *Objective*: Capture 5 full consecutive trading sessions ($\approx 10,000,000$ raw observations).
  * *Verification*: Complete dataset persisted in partitioned Parquet and verified for zero data corruption.
* `M6P3S2`: **Generate Formal Empirical Research Report Evaluating Hypotheses $H_1 \dots H_6$**
  * *Objective*: Publish comprehensive research report with Information Surface Matrices, decay curves, FDR-corrected $p$-values, and falsification verdicts.
  * *Verification*: Formal markdown & PDF report generated answering all 10 core quantitative research questions.


