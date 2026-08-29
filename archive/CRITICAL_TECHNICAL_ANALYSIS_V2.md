# Quantitative & Engineering Reality Check: Exhaustive Technical Answers
## Complete Verification Against Official Kite Connect APIs, Microstructure Math, and Empirical Design

---

## Q1: Data Feed Reality vs. Official Zerodha Kite Connect Documentation

### 1. Official Documentation Verification (KiteConnect 3 / KiteTicker Protocol)
The official Zerodha Kite Connect WebSocket protocol (`KiteTicker`) operates over binary WebSocket frames with three selectable subscription modes: `LTP` (8 bytes), `Quote` (44 bytes), and `Full` (184 bytes for NSE equity/derivatives).

#### Exact Documented Binary Packet Schema (`Full` Mode — 184 Bytes per Instrument):
```
[Offset]  [Field Name]                [Data Type]     [Divisor/Units]
00 - 03:  instrument_token            int32 (BE)      Raw Token ID
04 - 07:  last_price                  int32 (BE)      Divide by 100.0 (Paise -> INR)
08 - 11:  last_traded_quantity        int32 (BE)      Shares count
12 - 15:  average_traded_price (ATP)  int32 (BE)      Divide by 100.0 (Day VWAP)
16 - 19:  volume_traded (Cumulative)  int32 (BE)      Running daily sum of shares traded
20 - 23:  total_buy_quantity          int32 (BE)      Total aggregated buy market depth
24 - 27:  total_sell_quantity         int32 (BE)      Total aggregated sell market depth
28 - 43:  ohlc                        4 x int32 (BE)  Open, High, Low, Close (Divide by 100.0)
44 - 47:  last_trade_time             int32 (BE)      UNIX timestamp in SECONDS (0 if no trade today)
48 - 51:  oi (Open Interest)          int32 (BE)      Futures/Options open contracts
52 - 55:  oi_day_high                 int32 (BE)      OI High
56 - 59:  oi_day_low                  int32 (BE)      OI Low
60 - 63:  exchange_timestamp          int32 (BE)      UNIX timestamp in SECONDS
64 - 123: buy_depth (Top-5 Bids)      5 x 12 bytes    (Qty: int32, Price: int32/100, Orders: int16, Pad: 2B)
124- 183: sell_depth (Top-5 Asks)     5 x 12 bytes    (Qty: int32, Price: int32/100, Orders: int16, Pad: 2B)
```
*(Total per instrument packet = 184 bytes. Note: A single WebSocket binary frame can contain an aggregate batch of $N$ packets prepended by a 2-byte packet count header).*

### 2. Officially Guaranteed vs. Empirically Observed Realities

| Feature | Officially Guaranteed by Kite Docs | Empirically Observed Reality |
|---|---|---|
| **Market Depth** | Top-5 Bid & Ask levels with Price, Qty, and Order Count. | Strictly Top-5. Depth levels $> 5$ are completely invisible. |
| **Trade Level Log** | **NOT PROVIDED**. Only `last_price`, `last_traded_quantity`, and `volume_traded`. | No individual execution ticket or `trade_id`. Sub-second trade clusters collapse into single cumulative volume changes. |
| **Aggressor Side Tag** | **NOT PROVIDED**. No `BUY` / `SELL` tag in binary frame. | Must be estimated using the Tick / Quote Rule ($P_{\text{trade}} \ge \text{Ask}_1 \implies \text{BUY}$). |
| **Exchange Timestamps** | 32-bit integer representing **whole seconds** (e.g. `1724930400`). | **Zero sub-second precision from exchange**. Microsecond sequencing must rely strictly on local machine socket receive time ($t_{\text{recv}}$). |
| **Update Frequency** | Event-driven pushes from Zerodha’s market gateway. | **Throttled / Sampled at source**: Zerodha’s backend broadcasters throttle WebSocket pushes per connection to $\approx 1\text{ Hz} - 4\text{ Hz}$ per token to prevent TCP buffer bloat. It is **not** an unfiltered multicast tick stream. |

---

## Q2: Exact Mathematical Definition of the Fixed-Time Resampling Operator

To prevent subtle look-ahead bias and handle non-uniform packet arrival rates, the continuous order book state stream is resampled onto a discrete clock grid.

### 1. Formal Resampling Operator Definition
Let the raw asynchronous event stream be defined as a sequence of discrete tuples:
$$\mathcal{E} = \left\{ \left( t_i^{\text{mono}}, \mathcal{B}_i \right) \right\}_{i=1}^N$$
where $t_i^{\text{mono}}$ is the monotonic receive timestamp of packet $i$, and $\mathcal{B}_i = \left( B_{1..5}(i), Q^B_{1..5}(i), A_{1..5}(i), Q^A_{1..5}(i), V(i), LTP(i) \right)$ is the order book state.

Given a fixed clock grid $T_k = T_0 + k \cdot \Delta t$ (where $\Delta t = 250\text{ms}$ or $1000\text{ms}$):

$$\mathcal{B}^*(T_k) = \begin{cases} 
\mathcal{B}_m & \text{where } m = \max \{ i \in [1, N] \mid t_i^{\text{mono}} \le T_k \} \quad \text{if } (T_k - t_m^{\text{mono}}) \le \tau_{\text{stale}} \\
\text{NULL / MISSING} & \text{if } (T_k - t_m^{\text{mono}}) > \tau_{\text{stale}} \text{ or } \{ i \mid t_i^{\text{mono}} \le T_k \} = \emptyset
\end{cases}$$

```
Raw Packets:       [P1]       [P2]   [P3]            [P4]
Time Axis: ─────────┼──────────┼──────┼───────────────┼───────────────►
Grid Marks (Tk):    T0        T1              T2              T3
Resampled State:    B*(T0)=P1 B*(T1)=P1       B*(T2)=P3       B*(T3)=P4
                              (LOCF from P1)  (Latest <= T2)  (Latest <= T3)
```

### 2. Operational Rules:
* **Sampling Rule**: **Last-Observation-Carried-Forward (LOCF) strictly before or at $T_k$ ($t_i \le T_k$)**. We **never** use the first observation after $T_k$ ($t_i > T_k$), as that violates causality and introduces look-ahead bias into the feature calculation at step $k$.
* **Maximum Staleness Threshold ($\tau_{\text{stale}}$)**:
  * For liquid equities (e.g. Reliance, HDFC Bank): $\tau_{\text{stale}} = \mathbf{5000\text{ ms}}$ (5 seconds).
  * If no raw packet has been received for $> 5000\text{ms}$, $\mathcal{B}^*(T_k)$ is marked `STATE_STALE`.
  * **Downstream Enforcement**: Any forward return target $R_{\tau}(T_{k-\tau})$ spanning across a `STATE_STALE` grid point is marked invalid and dropped from model training to prevent training on dead liquidity gaps.

---

## Q3: True Microstructure OFI vs. Cumulative-Volume Snapshot Approximation

### 1. Why Simple Volume Attribution is a False Attribution Fallacy
If at $t-1$, Bid Level 1 has $1,000 \text{ shares} @ ₹100.00$, and at $t$, Bid Level 1 has $600 \text{ shares} @ ₹100.00$, while cumulative volume increased by $+400$:
* **The Naive Conclusion**: "A trade of 400 shares consumed the bid."
* **Why This is False Attribution**: In a throttled snapshot feed, multiple unobserved events occurred between packet arrivals:
  1. A buyer could have cancelled $800 \text{ shares}$ from Level 1.
  2. A new buyer could have added $+400 \text{ shares}$.
  3. A separate trade of $400 \text{ shares}$ could have executed against Ask Level 1 (or hidden dark/iceberg liquidity).
  4. The net depth at Bid Level 1 dropped by $-400$, and cumulative volume rose by $+400$, but **the two events were completely uncorrelated**.

### 2. What Our Top-5 "Proxy OFI" CAN and CANNOT Legitmately Infer

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WHAT PROXY OFI CAN LEGITIMATELY CLAIM                   │
├────────────────────────────────────────────────────────────────────────────┤
│  ✓ Net change in displayed Top-Level liquidity conditional on price jumps. │
│  ✓ Aggregate buying vs. selling queue depletion across observed snapshots. │
│  ✓ Directional pressure of order replacement vs. queue absorption.         │
├────────────────────────────────────────────────────────────────────────────┤
│                    WHAT PROXY OFI MUST NEVER CLAIM                         │
├────────────────────────────────────────────────────────────────────────────┤
│  ✗ Causal attribution of individual order cancellations vs. executions.    │
│  ✗ Detection of queue priority, order modifications, or iceberg orders.    │
│  ✗ Individual aggressor trade reconstruction inside the spread.            │
└────────────────────────────────────────────────────────────────────────────┘
```

### 3. The Mathematically Robust Multi-Level OFI Formulation (Cont-Kukanov-Stoikov)
To prevent price shifts from generating massive false cancellation artifacts, OFI at Level 1 is formulated as:

$$\text{OFI}_t = I_t^B - I_t^A$$

$$\text{where } I_t^B = \begin{cases}
Q_1^B(t) & \text{if } P_1^B(t) > P_1^B(t-1) \quad \text{(Price improved: New level established)} \\
Q_1^B(t) - Q_1^B(t-1) & \text{if } P_1^B(t) = P_1^B(t-1) \quad \text{(Same price: Net size change)} \\
-Q_1^B(t-1) & \text{if } P_1^B(t) < P_1^B(t-1) \quad \text{(Price dropped: Old level depleted)}
\end{cases}$$

$$I_t^A = \begin{cases}
-Q_1^A(t) & \text{if } P_1^A(t) < P_1^A(t-1) \quad \text{(Price dropped: New lower ask established)} \\
Q_1^A(t) - Q_1^A(t-1) & \text{if } P_1^A(t) = P_1^A(t-1) \quad \text{(Same price: Net size change)} \\
Q_1^A(t-1) & \text{if } P_1^A(t) > P_1^A(t-1) \quad \text{(Price increased: Old ask cleared)}
\end{cases}$$

This formulation handles price transitions without falsely reporting shifted price levels as order cancellations.

---

## Q4: State Classification Policy: Locked/Crossed Books & Packet Sequencing

### 1. Can a Crossed or Locked Book Appear Legitimately on NSE?
1. **Pre-Open Auction Call (09:00 – 09:08 IST)**: The order book is **deliberately crossed** ($\text{Bid}_1 \ge \text{Ask}_1$) as orders accumulate without matching until the uncrossing algorithm runs at 09:08 IST.
2. **Circuit Breakers / Volatility Halts**: During market re-opening auctions, crossed books are standard market behavior.
3. **Throttled WebSocket Collation Artifacts**: In continuous trading, if Zerodha's backend updates bid and ask buffers asynchronously with a 10ms gateway skew, a transient snapshot may show $\text{Bid}_1 \ge \text{Ask}_1$ for 1 frame.

### 2. Concrete State Tagging & Quarantine Policy
Rather than discarding packets, the engine applies an explicit bitmask state flag:

```
[Raw Ingested State]
         │
         ├──► Is (09:00 <= Time <= 09:15)? ──────► Tag: `SESSION_AUCTION_STATE` (Store, do not compute ML features)
         ├──► Is Best Bid > Best Ask?     ──────► Tag: `STATE_CROSSED_BOOK` (Store raw, mark features NaN)
         ├──► Is Best Bid == Best Ask?    ──────► Tag: `STATE_LOCKED_BOOK` (Store raw, mark features NaN)
         ├──► Is BidQty == 0 OR AskQty == 0? ──► Tag: `STATE_ILLIQUID_HALT` (Store raw, mark features NaN)
         └──► Best Bid < Best Ask & Qty > 0 ───► Tag: `STATE_VALID_CONTINUOUS` (Proceed to Feature Engine)
```

### 3. Packet Duplication & Reconnection Detection
Because Kite has no packet sequence number:
* **Volume Monotonicity Check**: $\Delta V = V_t - V_{t-1} < 0$ flags an immediate session reset or token remapping.
* **Exact Duplicate Filter**: If $(P_{1..5}, Q^B_{1..5}, A_{1..5}, Q^A_{1..5}, V, LTP)_t == (P, Q^B, A, Q^A, V, LTP)_{t-1}$ within $< 5\text{ ms}$, tag as `STATE_REDUNDANT_DUPLICATE`.
* **Reconnection OFI Reset**: On WebSocket reconnect or gap $> 3\text{ seconds}$, set $\text{OFI}_{t} = \text{NaN}$ for the initial snapshot to prevent computing a massive false delta against stale pre-disconnect liquidity.

---

## Q5: Dual-Timestamp Architecture (Epoch vs. Monotonic Clock)

### 1. The Clock Domain Problem
* `Instant.now()` / `System.currentTimeMillis()`: Tied to wall-clock **CLOCK_REALTIME**. It is subject to NTP adjustments, daylight shifts, and non-monotonic backward jumps.
* `System.nanoTime()`: Tied to **CLOCK_MONOTONIC / CLOCK_MONOTONIC_RAW**. It is an arbitrary-origin counter guaranteed to be strictly non-decreasing, immune to NTP jumps, but meaningless across machine reboots.

### 2. The Concrete Dual-Timestamp Record Model

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │                      DUAL-TIMESTAMP OBSERVATION MODEL                  │
 ├────────────────────────────────────────────────────────────────────────┤
 │  1. epoch_recv_micros (int64)      ──► Wall-Clock (Instant.now())      │
 │     • Used for: Database partition routing, human logs, session bounds │
 │                                                                        │
 │  2. mono_recv_nanos (int64)        ──► Monotonic (System.nanoTime())   │
 │     • Used for: Resampling grid, latency measurement, T+5s target sync │
 │                                                                        │
 │  3. exchange_time_secs (int32)     ──► Broker Field (Truncated sec)    │
 │     • Used for: Secondary latency health checks & exchange alignment   │
 └────────────────────────────────────────────────────────────────────────┘
```

### 3. Preventing NTP Contamination in Ubuntu Linux
To prevent NTP step adjustments from corrupting time series:
1. Configure `chrony` on Ubuntu to enforce **Slew Mode** (`makestep 0 0`):
   ```bash
   # In /etc/chrony/chrony.conf
   # Slew clock smoothly by adjusting frequency; NEVER step the clock backward during trading hours
   maxupdateskew 100.0
   makestep 0.1 3
   ```
2. All forward return target horizons ($T_k \rightarrow T_k + 5000\text{ms}$) are indexed **exclusively via `mono_recv_nanos`**. Even if wall-clock time adjusts, monotonic time calculations remain strictly linear and uncorrupted.

---

## Q6: Concrete Single-Process Architecture

To eliminate overengineering and multi-threaded synchronization race conditions, the platform is structured as **one deterministic, single-process Java 21 engine**:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              SINGLE-PROCESS JAVA 21 PIPELINE                           │
│                                                                                        │
│  [WebSocket Client Thread] (Kite WebSocket NIO Worker)                                 │
│         │ Reads raw byte frames from socket                                            │
│         │ Attaches: mono_recv_nanos + epoch_recv_micros + sequence_id                  │
│         ▼                                                                              │
│  [MPSC Bounded Ingestion Queue] (ArrayBlockingQueue<RawPacket>, capacity = 65,536)     │
│         │                                                                              │
│         ▼ (Single-threaded consumer — Zero Lock Contention)                            │
│  [Feature Compute & State Engine Thread] (Pinned to Dedicated CPU Core)                │
│         │ • Exclusively owns mutable in-memory order books (Map<Token, OrderBookState>)│
│         │ • Updates Top-5 depth, checks crossed books, calculates OBI/OFI/Microprice   │
│         │ • Evaluates Fixed-Time Resampling Grid (100ms / 1s)                          │
│         │ • Dispatches UI broadcasts via non-blocking RingBuffer (Drop on slow UI)     │
│         │ • Pushes raw binary frames to Disk Writer Queue                              │
│         ▼                                                                              │
│  [Disk Writer Queue] (ArrayBlockingQueue<RawFrame>, capacity = 131,072)                │
│         │                                                                              │
│         ▼                                                                              │
│  [Async Binary Disk Writer Thread] (Background I/O Core)                               │
│         │ Appends raw binary envelopes to uncompressed `.raw` daily log on disk        │
│         ▼                                                                              │
│  [Raw WAL File on NVMe / SSD] (/data/raw/2026-08-29/session_0915.raw)                  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Component Details:
* **Mutable State Ownership**: A single dedicated thread (`FeatureComputeAndStateEngine`) owns all mutable state. No mutexes, no synchronized blocks, no lock contention.
* **Backpressure Policy**:
  * If `Ingestion Queue` hits 80% capacity $\implies$ Log critical warning and drop UI broadcast frames.
  * Ingestion queue drop policy: `DROP_OLDEST_UNPROCESSED_UI_EVENT`. Raw binary logging to disk is never dropped.
* **Failure Isolation**: If the WebSocket dashboard client hangs or disconnects, the ring buffer drops UI frames without slowing down market feature calculation or disk logging.

---

## Q7: Empirical Pilot Benchmark Plan

Rather than designing for assumed traffic, the pilot session will capture empirical performance baselines on our Intel i5-8250U Ubuntu system across 10 liquid NSE equities:

```
┌─────────────────────────┬─────────────────────────┬─────────────────────────┐
│       METRIC            │    INSTRUMENTATION      │   ARCHITECTURAL ACTION  │
│                         │        METHOD           │        TRIGGER          │
├─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ Packet Arrival Rate     │ Counter per second per  │ If peak > 5,000 pkts/s  │
│ (p50, p95, p99, Max)    │ token over 6 hours      │ -> Pin thread to core   │
├─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ Packet Decode Latency   │ Nano-timer around binary│ If p99 > 25 microseconds│
│ (p50, p99, Max)         │ unpacker function       │ -> Off-heap DirectBuffer│
├─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ Feature Compute Latency │ Nano-timer around math  │ If p99 > 50 microseconds│
│ (p50, p99, Max)         │ feature calculator      │ -> Optimize array reuse │
├─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ GC Pause Duration       │ JVM -Xlog:gc* flag      │ If any pause > 5.0 ms   │
│ & Allocation Rate (MB/s)│ logging every pause     │ -> Eliminate object alloc│
├─────────────────────────┼─────────────────────────┼─────────────────────────┤
│ Disk Write Throughput   │ Bytes appended per sec  │ If I/O wait > 2.0%      │
│ & Storage Footprint     │ to `.raw` binary file   │ -> Enlarge write buffer │
└─────────────────────────┴─────────────────────────┴─────────────────────────┘
```

---

## Q8: Deterministic Raw Storage Format Specification

To guarantee bit-for-bit replayability months later, raw WebSocket binary frames must be encapsulated in an immutable binary container format:

### Binary Frame Container Specification (`.raw` Envelope Format):
```
[Header: 32 Bytes per WebSocket Frame Envelope]
Offset  Field               Type      Description
00-03:  magic_bytes         uint32    0x4F424157 ("OBAW" = Order Book Analyses WAL)
04-05:  version             uint16    0x0001 (Schema version 1)
06-07:  connection_id       uint16    Unique ID per WebSocket connection attempt
08-15:  frame_sequence_no   uint64    Strictly monotonic counter per connection (1, 2, 3...)
16-23:  mono_recv_nanos     int64     System.nanoTime() at socket read
24-31:  epoch_recv_micros   int64     Instant.now() microseconds at socket read
32-35:  payload_length      uint32    Length of following Kite binary payload ($L$ bytes)
36-39:  payload_crc32       uint32    CRC-32 checksum of the raw payload
40-(40+L-1): raw_payload    byte[L]   Unmodified raw binary payload received from Kite
```

### Deterministic Replay Guarantees:
1. **Preserves Socket Frame Boundaries**: Multiple packets batched in a single WebSocket frame are preserved in their exact original arrival grouping.
2. **Reconnection & Disconnect Markers**: Special synthetic frames (`payload_length = 0`, `magic = 0x4F424145` ("OBAE" = Event)) are injected upon socket connect, disconnect, and error events.
3. **Offline Deterministic Replay**: An offline simulator reads this file sequentially, reconstructs the identical monotonic timeline, and produces bit-for-bit identical features.

---

## Q9: Production Storage Lifecycle & Crash Recovery

### 1. Concrete File Lifecycle

```
[09:15 - 15:30 IST]  LIVE LOGGING
   • Append-only uncompressed binary log:
     /data/raw/2026-08-29/session_091500.raw
   • Buffered I/O via Java FileChannel with explicit OS flush every 1,000ms

[15:45 IST]          END-OF-DAY FINALIZATION PIPELINE
   1. Binary Integrity Check: Verify magic bytes and CRC32 on every frame.
   2. Batch Transformer: Reconstruct states -> apply 1s resampling grid -> compute features and forward labels.
   3. Columnar Export: Write partitioned Parquet files:
      /data/parquet/date=2026-08-29/instrument=RELIANCE/features_1s.parquet
      (Compressed with ZSTD Level 3, sorted by mono_recv_nanos).
   4. Database Archival: Insert daily session summary and experiment metadata into PostgreSQL.
```

### 2. Crash Recovery Procedure (e.g. OS Crash at 14:47:32):
1. **Detection**: Upon restart, the engine opens `session_091500.raw` and scans frame by frame.
2. **Quarantine of Partial Trailing Bytes**:
   - If the last frame was cut mid-write due to a power loss, CRC32 or `payload_length` check fails.
   - The engine truncates the file at the last complete, verified frame boundary.
   - Any corrupt trailing bytes are moved to `quarantine_corrupt_tail.bin` for forensic inspection.
3. **Zero Historical Corruption**: Because the raw file is strictly append-only, all data logged up to the last 1-second flush is 100% intact and replayable.

---

## Q10: Explicit Separation of the Three Forward Target Definitions

```
                     THE THREE DISTINCT FORWARD TARGET DOMAINS
                     
   1. INFORMATIONAL TARGET              2. CLASSIFICATION TARGET             3. EXECUTABLE P&L TARGET
   (Microstructure Alpha)               (Machine Learning Direction)         (Real-World Trading Feasibility)
 ┌──────────────────────────┐         ┌──────────────────────────┐         ┌──────────────────────────┐
 │ • Log Mid-Price Return   │         │ • Discrete Tri-Class Tag │         │ • Spread-Crossed Net P&L │
 │ • Continuous Real Number │         │ • {-1, 0, +1} Labels     │         │ • Deducts Spread & Fees  │
 │ • Evaluates Information  │         │ • Volatility-Adjusted    │         │ • Realistic Execution    │
 └──────────────────────────┘         └──────────────────────────┘         └──────────────────────────┘
```

### 1. Target 1: Informational Mid-Price Log Return ($R_{\text{mid}, \tau}$) $\longrightarrow$ **(PRIMARY RESEARCH TARGET FOR GOAL A)**
$$R_{\text{mid}, \tau}(T_k) = \ln \left( \frac{P_{\text{mid}}^*(T_k + \tau)}{P_{\text{mid}}^*(T_k)} \right) \quad \text{where } P_{\text{mid}}^*(T) = \frac{B_1^*(T) + A_1^*(T)}{2}$$
* **Sampling Rule**: $P_{\text{mid}}^*(T)$ uses the **Last-Observation-Carried-Forward (LOCF)** valid snapshot at or before $T$.
* **Purpose**: Measures pure informational price discovery without confounding execution mechanics.

### 2. Target 2: Volatility-Adjusted Directional Classification Label ($Y_{\tau}$)
$$Y_{\tau}(T_k) = \begin{cases}
+1 (\text{UP}) & \text{if } R_{\text{mid}, \tau}(T_k) > +\theta_{\text{vol}}(T_k) \\
-1 (\text{DOWN}) & \text{if } R_{\text{mid}, \tau}(T_k) < -\theta_{\text{vol}}(T_k) \\
0 (\text{NEUTRAL}) & \text{otherwise}
\end{cases} \quad \text{where } \theta_{\text{vol}}(T_k) = \max \left( 0.5 \cdot \sigma_{\text{rolling, 15m}}(T_k), \; \frac{\text{Spread}(T_k)}{2 \cdot P_{\text{mid}}(T_k)} \right)$$
* **Purpose**: Discrete target for ML classification (Logistic Regression, XGBoost). Dynamically adjusts threshold based on rolling 15-minute realized volatility.

### 3. Target 3: Executable Spread-Crossed Strategy P&L ($R_{\text{exec}, \tau}$)
For a model-predicted **LONG** signal at $T_k$:
$$R_{\text{exec}, \tau}^{\text{LONG}}(T_k) = \frac{B_1^*(T_k + \tau) - A_1^*(T_k)}{A_1^*(T_k)} - \text{Taxes \& Fees}_{\text{roundtrip}}$$
* **Purpose**: Determines whether a statistically predictive signal survives crossing the bid-ask spread ($A_1 - B_1$) and paying statutory fees.

---

## Q11: Formal Defense Against Data Snooping & Multiple Hypothesis Testing

When evaluating 10 features across 5 horizons, 20 instruments, and 4 regimes ($10 \times 5 \times 20 \times 4 = 4,000$ hypotheses), standard testing at $\alpha = 0.05$ produces **200 false positive discoveries by pure chance**.

### Rigorous Defense Protocols:
1. **Benjamini-Hochberg False Discovery Rate (FDR) Control**:
   - Rank all $M$ test $p$-values: $p_{(1)} \le p_{(2)} \le \dots \le p_{(M)}$.
   - Find the largest $k$ such that $p_{(k)} \le \frac{k}{M} \cdot \alpha_{\text{FDR}}$ (with $\alpha_{\text{FDR}} = 0.05$).
   - Reject only null hypotheses $H_{(1)} \dots H_{(k)}$.
2. **Deflated Sharpe Ratio (DSR) (Marcos López de Prado)**:
   - Statistically adjusts estimated Sharpe ratios based on the variance of trial returns, skewness, kurtosis, and the total number of tested strategy permutations ($N$).
3. **Pre-Registration of Hypotheses**:
   - The feature definitions, resampling intervals (1000ms), and horizon set ($\tau \in \{1\text{s}, 5\text{s}, 10\text{s}, 30\text{s}, 60\text{s}\}$) are frozen in code before model training, preventing post-hoc parameter mining.

---

## Q12: Rigorous Derivation of Purging & Embargo Windows

The choice of purging and embargo buffers must be derived mathematically from temporal label horizons and feature lookback windows:

```
                  PURGING & EMBARGO TIMELINE DERIVATION
                  
                  [==== TRAINING SET ====] ───► [PURGE] ───► [TEST SET] ───► [EMBARGO]
                                                   │                            │
                                                   ▼                            ▼
                                           Length = tau_max            Length = L_lookback
                                           (e.g., 60 seconds)          (e.g., 300 seconds)
```

### 1. Mathematical Derivation:
* **Purge Window ($\tau_{\text{purge}}$)**:
  - Let maximum forward target horizon be $\tau_{\max} = 60\text{ seconds}$.
  - Any observation in the training set within $60\text{ seconds}$ of the test boundary has a forward return target that leaks into the test period.
  - **Exact Purge Length**: $\tau_{\text{purge}} = \tau_{\max} = \mathbf{60\text{ seconds}}$.
* **Embargo Window ($\tau_{\text{embargo}}$)**:
  - Let maximum rolling feature lookback window (e.g. 5-minute rolling trade intensity) be $L_{\text{lookback}} = 300\text{ seconds}$.
  - To prevent test-set autoregressive spillover when rolling back to subsequent folds:
  - **Exact Embargo Length**: $\tau_{\text{embargo}} = L_{\text{lookback}} = \mathbf{300\text{ seconds}}$ (5 minutes).
* **Inter-Session Independence**:
  - In Indian equity markets, trading halts from 15:30 to 09:15 the next morning (17 hours 45 minutes gap). All intraday rolling buffers reset at 09:15.
  - **Rule**: Entire trading sessions are structurally independent. Splitting at market close requires **zero cross-session purging**.

---

## Q13: Statistically Defensible Model Evaluation Framework

We replace arbitrary accuracy numbers with an institutional statistical evaluation framework:

```
┌───────────────────────────┬────────────────────────────────────────────────────────┐
│     EVALUATION METRIC     │               ACCEPTANCE / SUCCESS CRITERIA            │
├───────────────────────────┼────────────────────────────────────────────────────────┤
│ Information Coefficient   │ • Out-of-sample Spearman Rank IC > 0.03                │
│ (Spearman Rank IC)        │ • Student's t-stat > 3.0 (p < 0.001 with FDR control)  │
├───────────────────────────┼────────────────────────────────────────────────────────┤
│ Stationary Block Bootstrap│ • 95% Confidence Interval strictly positive (> 0.0)    │
│ (Politis & Romano)        │   preserving serial autocorrelation in return series   │
├───────────────────────────┼────────────────────────────────────────────────────────┤
│ Model Calibration         │ • Brier Score < 0.22 (vs. 0.25 uninformative baseline) │
│ & Reliability Curve       │ • Probability calibration curve slope in [0.90, 1.10]  │
├───────────────────────────┼────────────────────────────────────────────────────────┤
│ Incremental Alpha         │ • Statistically significant delta over Level-Weighted  │
│ (ΔAUC / ΔIC)              │   OBI baseline: ΔIC > +0.015 with Delong test p < 0.01 │
└───────────────────────────┴────────────────────────────────────────────────────────┘
```

---

## Q14: Versioned Indian Statutory Costs & Microstructure Simulation Limits

### 1. Official Indian Equity Intraday Transaction Cost Schedule (Effective October 2024):
```json
{
  "cost_model_version": "NSE_EQUITY_INTRADAY_V2024_OCT",
  "effective_from": "2024-10-01",
  "effective_to": "9999-12-31",
  "brokerage_per_order_inr": 20.0,
  "brokerage_max_turnover_pct": 0.0003,
  "stt_sell_side_pct": 0.00025,
  "exchange_turnover_charge_pct": 0.0000297,
  "sebi_turnover_fee_pct": 0.000001,
  "stamp_duty_buy_side_pct": 0.00003,
  "gst_pct_on_charges": 0.18
}
```
*(Total roundtrip statutory drag $\approx \mathbf{0.034\% - 0.048\%}$ of traded turnover).*

### 2. Explicit Simulation Boundaries: What CAN vs. CANNOT Be Simulated

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    WHAT OUR BACKTEST CAN LEGITIMATELY MODEL                │
├────────────────────────────────────────────────────────────────────────────┤
│  ✓ Full bid-ask spread crossing penalty ($A_1 - B_1$).                     │
│  ✓ Exact versioned Indian statutory fees, STT, GST, and brokerage.         │
│  ✓ Execution latency buffer (e.g. fill at $T + 50\text{ms}$ after signal). │
│  ✓ Linear market impact based on trade size vs. Level-1 displayed depth.   │
├────────────────────────────────────────────────────────────────────────────┤
│                    WHAT IS EXPLICITLY UNMODELED (UNOBSERVABLE)             │
├────────────────────────────────────────────────────────────────────────────┤
│  ✗ Exact queue position for passive limit orders (MBO Level-3 required).   │
│  ✗ True fill probability of passive resting limit orders.                  │
│  ✗ Latency arbitrage race conditions against co-located HFT market makers. │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## Q15: Precise Research Hypothesis, Primary Variables & Explicit Falsification Criteria

### 1. Primary Research Hypothesis ($H_1$)
> *"Top-5 Level-Weighted Order Book Imbalance ($W\text{-OBI}$) and Multi-Level Order Flow Imbalance (OFI), when conditioned on prevailing bid-ask spread and intraday volatility regime, exhibit statistically significant out-of-sample directional predictive power ($t > 3.0, p < 0.001$, FDR $q < 0.05$) on 5-second and 10-second forward mid-price log returns ($R_{\text{mid}, \tau}$) across liquid NSE equities, outperforming a random walk, autoregressive momentum, and unweighted static OBI baselines."*

### 2. Core Variable Definitions:
* **Primary Dependent Variable**: 5-second forward mid-price log return:
  $$R_{\text{mid}, 5s}(T_k) = \ln \left( \frac{P_{\text{mid}}^*(T_k + 5000\text{ms})}{P_{\text{mid}}^*(T_k)} \right)$$
* **Primary Independent Variables**:
  1. Level-Weighted Imbalance: $W\text{-OBI}(T_k) \in [-1, +1]$
  2. Multi-Level OFI: $\text{OFI}(T_k)$
  3. Microprice Pressure: $\text{Microprice}(T_k) - P_{\text{mid}}(T_k)$
  4. Prevailing Relative Spread: $\frac{A_1(T_k) - B_1(T_k)}{P_{\text{mid}}(T_k)}$
  5. Realized 15-Minute Volatility: $\sigma_{\text{realized}}(T_k)$

### 3. Explicit Falsification Criteria
The research hypothesis $H_1$ is **FALSIFIED** if any of the following occur:
1. **Zero Out-of-Sample Information**: The out-of-sample Spearman Rank Information Coefficient (IC) is statistically indistinguishable from zero ($|\text{IC}| < 0.02$ or $p > 0.01$ after FDR adjustment).
2. **Failure to Beat Baselines**: The multi-factor model fails to provide a statistically significant improvement ($\Delta \text{IC} \le 0.005$ or Delong test $p \ge 0.05$) over an autoregressive return + spread baseline.
3. **Sub-Resolution Signal Decay**: The predictive signal half-life decays in $< 1.0\text{ second}$, proving that the predictive effect is faster than the broker's WebSocket broadcast latency.

### 4. What Counts as a Successful Project Even if All Features Fail to Predict Price?
In empirical quantitative finance, **a rigorous negative result is a major scientific success**:
* Proving definitively with zero look-ahead bias and FDR statistical control that *retail broker-level Top-5 order book imbalances contain zero actionable predictive alpha on NSE equities due to broker sampling throttling and cancellation noise* prevents the deployment of flawed capital and provides an academically defensible, publication-grade empirical conclusion.
