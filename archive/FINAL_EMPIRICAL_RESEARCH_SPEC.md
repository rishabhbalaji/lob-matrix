# Empirical Market Microstructure Platform: Final Research Specification
## Quantitative Framing, Formal Operators, and Lean System Architecture

---

## 1. Executive Research Framing

### Core Research Objective
> **"An Empirical Investigation into the Predictive Information Retained in Broker-Distributed Top-5 Order-Book Snapshots Under Temporal Sampling, Latency, and Execution Constraints."**

The objective is **not** to claim microsecond exchange order-flow reconstruction from a retail WebSocket API. Rather, the system investigates:
$$\text{Exchange State} \longrightarrow \text{Broker Distribution \& Sampling} \longrightarrow \text{Observed Top-5 Snapshot} \longrightarrow \text{Feature Space} \longrightarrow \text{Predictive Information } I(\text{Features}; R_{t+\tau})$$

---

## 2. The Central Experimental Dimension: Temporal Sampling vs. Forecast Horizon

The platform will empirically compute the **Information Surface Matrix** across sampling frequencies ($\Delta t$) and forward horizons ($\tau$):

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
*Core Scientific Question*: **At what temporal resolution does broker-provided market depth stop containing useful forward predictive information?**

---

## 3. Formal Data Ingestion & Dual-Timestamp Model

### 1. Ingestion Definitions
* **Client Arrival Time**: Let $t_i^{\text{mono}}$ be the local monotonic time (`System.nanoTime()`) and $t_i^{\text{epoch}}$ be the wall-clock time (`Instant.now()`) recorded at the socket read event.
* **Update Frequency Metric**: Defined as an empirical research metric, not an assumed constant:
  $$\Delta t_i = t_i^{\text{mono}} - t_{i-1}^{\text{mono}}$$
  The pilot instruments and reports empirical percentiles ($p_1, p_5, p_{50}, p_{95}, p_{99}, \max$).

### 2. The Deterministic Resampling Operator
At session start, initialize base monotonic time $t_0^{\text{mono}}$. Define the discrete grid:
$$T_k^{\text{mono}} = t_0^{\text{mono}} + k \cdot \Delta t$$

The state at grid point $k$ is given by Last-Observation-Carried-Forward (LOCF):
$$\mathcal{B}^*(T_k) = \mathcal{B}_m \quad \text{where } m = \max \{ i \mid t_i^{\text{mono}} \le T_k^{\text{mono}} \}$$

#### Freshness / Snapshot Age Feature:
$$\text{Age}(T_k) = T_k^{\text{mono}} - t_m^{\text{mono}}$$
* Recorded as a conditioning variable `snapshot_age_ms`.
* If $\text{Age}(T_k) > \tau_{\text{stale}}$ (where $\tau_{\text{stale}} = 5000\text{ms}$), $\mathcal{B}^*(T_k)$ is marked `STALE_DROPPED`.

---

## 4. Microstructure Features & Formal OFI Definitions

### 1. Snapshot-Derived OFI Proxy (Level 1)
To account for price transitions without causal event attribution:
$$\text{OFI}_t^{(1)} = I_t^B - I_t^A$$
$$\text{where } I_t^B = \begin{cases} Q_1^B(t) & \text{if } P_1^B(t) > P_1^B(t-1) \\ Q_1^B(t) - Q_1^B(t-1) & \text{if } P_1^B(t) = P_1^B(t-1) \\ -Q_1^B(t-1) & \text{if } P_1^B(t) < P_1^B(t-1) \end{cases}$$
$$I_t^A = \begin{cases} -Q_1^A(t) & \text{if } P_1^A(t) < P_1^A(t-1) \\ Q_1^A(t) - Q_1^A(t-1) & \text{if } P_1^A(t) = P_1^A(t-1) \\ Q_1^A(t-1) & \text{if } P_1^A(t) > P_1^A(t-1) \end{cases}$$

### 2. Multi-Level OFI Formulations (Pre-Registered)
$$\text{ML-OFI}_t = \sum_{\ell=1}^5 w_\ell \left( I_{t, \ell}^B - I_{t, \ell}^A \right)$$
* **Variant A (ML-OFI-Uniform)**: $w_\ell = \frac{1}{5} = 0.20$
* **Variant B (ML-OFI-Exponential)**: $w_\ell = \frac{e^{-\lambda(\ell - 1)}}{\sum_{j=1}^5 e^{-\lambda(j-1)}}$ with $\lambda = 0.5$ ($w = [0.437, 0.265, 0.161, 0.098, 0.059]$).

---

## 5. Session State Machine & Book Integrity Policy

```
[Incoming State Snapshot]
         │
         ├──► Market Phase:
         │      ├── 09:00 - 09:08 IST ──► PRE_OPEN_ORDER_ENTRY (Store raw, no ML)
         │      ├── 09:08 - 09:15 IST ──► PRE_OPEN_MATCHING (Store raw, no ML)
         │      ├── 09:15 - 15:30 IST ──► CONTINUOUS_TRADING
         │      └── 15:30 - 16:00 IST ──► POST_CLOSE_AUCTION
         │
         └──► Book Condition Flag:
                ├── BidQty == 0 OR AskQty == 0 ──► STATE_EMPTY_SIDE
                ├── Best Bid > Best Ask        ──► STATE_CROSSED (Record cross_duration_ms)
                ├── Best Bid == Best Ask       ──► STATE_LOCKED (Record lock_duration_ms)
                └── Best Bid < Best Ask        ──► STATE_VALID_SPREAD
```

---

## 6. Immutable Binary WAL Format (40-Byte Fixed Header)

Every raw WebSocket binary envelope is stored with a fixed **40-byte header**:

```
[Offset] [Field Name]             [Type]    [Description]
00 - 03: magic_bytes              uint32    0x4F424157 ("OBAW")
04 - 05: schema_version           uint16    0x0001
06 - 07: connection_id            uint16    ID per socket session
08 - 15: global_capture_sequence  uint64    Strictly monotonic global capture counter
16 - 23: mono_recv_nanos          int64     System.nanoTime() at socket read
24 - 31: epoch_recv_micros        int64     Instant.now() epoch microseconds
32 - 35: payload_length           uint32    Byte length ($L$) of raw Kite frame
36 - 39: payload_crc32            uint32    CRC-32 checksum of raw frame
40 - (40+L-1): raw_payload        byte[L]   Raw unmodified binary payload
```
*Total Envelope Size = $40 + L$ bytes.*

---

## 7. Lean Single-Process Architecture & Explicit Queue Telemetry

```
┌────────────────────────────────────────────────────────────────────────┐
│               SINGLE-PROCESS, OWNERSHIP-PARTITIONED ENGINE             │
│                                                                        │
│  [Socket Ingestion Thread]                                             │
│         │ Assigns global_capture_sequence & mono_recv_nanos            │
│         ▼                                                              │
│  [MPSC Ingestion Queue] (Bounded ArrayBlockingQueue, cap = 65,536)     │
│         │                                                              │
│         ▼                                                              │
│  [State & Feature Engine Thread] (Exclusive single-thread state owner) │
│         │ • Evaluates session state & crossed books                    │
│         │ • Computes Multi-Level OFI, W-OBI, Microprice, Trade Strength│
│         │ • Evaluates Fixed-Time Grid (LOCF)                           │
│         │ • Dispatches UI frames (Drop oldest if UI congested)         │
│         │ • Dispatches Raw Envelopes to Disk Writer Queue              │
│         ▼                                                              │
│  [Disk Writer Queue] (Bounded ArrayBlockingQueue, cap = 131,072)       │
│         │                                                              │
│         ▼                                                              │
│  [Async Disk Writer Thread]                                            │
│         │ Appends 40-byte envelopes to daily `.raw` WAL file           │
│         │ Invokes FileChannel.force(false) every 1,000ms               │
└────────────────────────────────────────────────────────────────────────┘
```

### Explicit Queue Drop Telemetry
If the disk writer falls behind and the queue exceeds capacity:
1. `INGESTION_OVERFLOW`: Counter incremented when memory bounds are hit.
2. `RAW_FRAME_DROPPED`: Explicit log entry with monotonic timestamp of dropped data.
3. System integrity flag `HAS_DISCONTINUITIES` set to `true` for that session to alert downstream research.

---

## 8. Target Construction & Horizon-Normalized Volatility Scaling

### 1. Primary Informational Target ($R_{\text{mid}, \tau}$)
$$R_{\text{mid}, \tau}(T_k) = \ln \left( \frac{P_{\text{mid}}^*(T_k + \tau)}{P_{\text{mid}}^*(T_k)} \right)$$

### 2. Horizon-Scaled Volatility Threshold ($\theta_\tau$)
To ensure threshold comparability across different horizons $\tau$, volatility is scaled:
$$\sigma_\tau(T_k) = \sigma_{\Delta t, \text{realized}}(T_k) \cdot \sqrt{\frac{\tau}{\Delta t}}$$
$$\theta_\tau(T_k) = \max \left( c \cdot \sigma_\tau(T_k), \; \frac{\text{Spread}(T_k)}{2 \cdot P_{\text{mid}}(T_k)} \right) \quad \text{with } c = 0.5$$

Directional Label:
$$Y_\tau(T_k) = \begin{cases} +1 & \text{if } R_{\text{mid}, \tau}(T_k) > +\theta_\tau(T_k) \\ -1 & \text{if } R_{\text{mid}, \tau}(T_k) < -\theta_\tau(T_k) \\ 0 & \text{otherwise} \end{cases}$$

---

## 9. Information-Interval Overlap Purging & Embargo Protocol

Instead of rigid arbitrary constants, purging is based on **Information Interval Overlaps**:

```
Sample k Info Intervals:
Feature Interval: I_k^feature = [T_k - L_lookback, T_k]
Label Interval:   I_k^label   = [T_k, T_k + tau]

Prohibited Test Interval: [T_test_start, T_test_end]
```

### The Purging Rule:
* Drop training sample $k$ if:
  $$\left( I_k^{\text{label}} \cap [T_{\text{test\_start}}, T_{\text{test\_end}}] \ne \emptyset \right) \quad \text{OR} \quad \left( I_k^{\text{feature}} \cap [T_{\text{test\_start}}, T_{\text{test\_end}}] \ne \emptyset \right)$$
* **Trading Day Boundary**: Sessions are independent. Between Day $D$ close (15:30 IST) and Day $D+1$ open (09:15 IST), interval overlap is naturally empty $\implies$ Zero cross-session purge required.

---

## 10. Formal Research Hypotheses & Falsification Criteria

### Primary Hypothesis ($H_1$)
> **"Conditional on prevailing relative spread and realized volatility regime, snapshot-derived top-five order-book imbalance ($W\text{-OBI}$) exhibits a positive and statistically significant out-of-sample rank association (Spearman Rank IC $> 0$, FDR $q < 0.05$) with 5-second forward mid-price log returns ($R_{\text{mid}, 5s}$) in a pre-registered universe of liquid NSE equities."**

### Pre-Registered Secondary Hypotheses:
* **$H_2$ (OFI Incremental Value)**: Snapshot-derived Multi-Level OFI adds statistically significant incremental Information Coefficient ($\Delta \text{IC} > 0.015, p < 0.01$) over static $W\text{-OBI}$.
* **$H_3$ (Level Weighting)**: Decaying level weights ($W\text{-OBI}$) outperform simple Level-1 OBI ($\text{OBI}_1$).
* **$H_4$ (Spread Regime Sensitivity)**: The rank correlation is significantly stronger in narrow-spread regimes than in wide-spread regimes.
* **$H_5$ (Signal Decay Surface)**: Predictive rank IC peaks in the $\tau \in [1\text{s}, 5\text{s}]$ window and monotonically decays toward zero as $\tau \rightarrow 60\text{s}$.
* **$H_6$ (Economic Frictional Viability)**: Net spread-crossed returns after versioned statutory Indian fees ($R_{\text{exec}}$) remain positive on top-decile signal confluences.

### Explicit Falsification:
If $H_1$ fails to achieve $|\text{IC}| > 0.02$ ($p < 0.01$) or fails to outperform an autoregressive momentum baseline, the hypothesis is formally rejected, establishing that *broker-level Top-5 snapshots lack short-horizon predictive information on the NSE*.
