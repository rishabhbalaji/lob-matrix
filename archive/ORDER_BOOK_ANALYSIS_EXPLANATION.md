# Quantitative Order Book Analysis & Microstructure Research Platform
## Complete Technical Specification & Architectural Breakdown

---

## 1. Executive Summary: What Exactly Is This Project?

This project is an **institutional-grade Quantitative Market Microstructure Analysis and Research Platform** designed for high-frequency order book and trade flow analysis. 

The primary objective is **not** to build a simplistic trading bot, but to build a **statistically rigorous analytical system** that captures, synchronizes, and evaluates:
1. **Level-2 Market Depth (Top 5 Bids and Asks)**
2. **Executed Trade/Tick Flow & Aggressor Side Classification (Buy vs. Sell Pressure)**
3. **Dynamic Order Flow Changes (Order additions, cancellations, and OFI)**
4. **Short-Term Predictive Relationships with Future Price Returns (1s, 5s, 10s, 30s, 60s horizons)**

### Core Quantitative Philosophy
In retail trading, people often make naive assumptions like *"more bids than asks means the price will rise"*. In real financial markets (such as the National Stock Exchange of India via Zerodha Kite Connect), displayed liquidity can be cancelled in milliseconds, spoofed, or overwhelmed by aggressive market orders.

This platform replaces naive intuition with **empirical, out-of-sample statistical proof**, answering:
- *Does order book imbalance actually predict short-term price direction and magnitude?*
- *How fast does the predictive signal decay (1s vs. 5s vs. 60s)?*
- *What happens when trade strength confirms or conflicts with order book imbalance?*
- *Is the signal economically profitable after accounting for bid-ask spread, slippage, latency, and transaction costs?*

---

## 2. High-Level System Architecture

```
                      ┌─────────────────────────────────────────┐
                      │        Zerodha Kite WebSocket Feed      │
                      │    (Live L2 Depth & Tick-by-Tick Trades)│
                      └────────────────────┬────────────────────┘
                                           │
                                           ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        JAVA SPRING BOOT INGESTION & CORE BACKEND                       │
│                                                                                        │
│  ┌────────────────────────┐   ┌────────────────────────┐   ┌────────────────────────┐  │
│  │   WebSocket Client &   │   │ High-Throughput Buffer │   │  Microsecond Clock &   │  │
│  │   Binary Tick Parser   ├───► (LMAX Disruptor/Queue) ├───► Time-Series Synchronizer│  │
│  └────────────────────────┘   └────────────────────────┘   └───────────┬────────────┘  │
│                                                                        │               │
│  ┌─────────────────────────────────────────────────────────────────────┴────────────┐  │
│  │                     Real-Time Microstructure Feature Engine                      │  │
│  │  • Mid Price, Spread, Relative Spread                                            │  │
│  │  • 5-Level Depth & Level-Weighted Imbalance (W-OBI)                              │  │
│  │  • Microprice & Microprice Pressure                                              │  │
│  │  • Aggressor Side Trade Classification (Tick/Quote Rule)                         │  │
│  │  • Buy/Sell Pressure & Trade Strength (-1.0 to +1.0)                             │  │
│  │  • Trade & Volume Intensity (1s, 5s, 10s, 30s, 60s rolling windows)             │  │
│  │  • Order Flow Imbalance (OFI: ΔBid - ΔAsk) & Liquidity Dynamics                  │  │
│  │  • Multi-Factor Confluence Composite Strength Score                              │  │
│  └─────────────────────────────────────┬────────────────────────────────────────────┘  │
│                                        │                                               │
│  ┌─────────────────────────────────────┴────────────────────────────────────────────┐  │
│  │                 Persistence & Data Pipeline (Spring Data / JDBC)                 │  │
│  │  • order_book_snapshot (L2 Top 5)                                                │  │
│  │  • trade_tick (Tick execution + Aggressor tag)                                   │  │
│  │  • market_features (Synchronized feature time-buckets)                           │  │
│  │  • price_targets (Strict no-lookahead forward returns & labels)                  │  │
│  └─────────────────────────────────────┬────────────────────────────────────────────┘  │
│                                        │                                               │
│  ┌─────────────────────────────────────┴────────────────────────────────────────────┐  │
│  │                      Analytical & Research Services (Java / REST / WS)           │  │
│  │  • Pearson & Spearman Correlation Matrices                                       │  │
│  │  • Lead/Lag Decay Analysis                                                       │  │
│  │  • Market Regime & Conditional Segmentation (Volatility, Volume, Spread, Time)   │  │
│  │  • Trade Strength × OBI Confluence Matrix                                        │  │
│  │  • Backtesting Engine (Out-of-sample walk-forward: 60% Train / 20% Val / 20% Test) │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────┬─────────────────────────────────────────────┘
                                           │
               ┌───────────────────────────┴───────────────────────────┐
               ▼                                                       ▼
┌───────────────────────────────┐                       ┌───────────────────────────────┐
│     PostgreSQL / TimescaleDB  │                       │   Interactive Research UI     │
│   (Historical Source of Truth)│                       │ (Live L2 Book, Imbalance vs   │
│   & High-Speed Parquet Export │                       │  Price, Return Probabilities) │
└───────────────────────────────┘                       └───────────────────────────────┘
```

---

## 3. Mathematical Foundations & Microstructure Metrics

The specification defines precise formulas for computing market microstructure features:

### 3.1 Basic Order Book Geometry
- **Best Bid ($B_1$) & Best Ask ($A_1$)**: The highest buy price and lowest sell price.
- **Mid Price ($P_{mid}$)**:
  $$P_{mid} = \frac{B_1 + A_1}{2}$$
- **Absolute Spread**:
  $$\text{Spread} = A_1 - B_1$$
- **Relative Spread**:
  $$\text{Relative Spread} = \frac{A_1 - B_1}{P_{mid}}$$
- **5-Level Total Depth**:
  $$\text{Bid Depth} = \sum_{i=1}^5 \text{BidQty}_i, \quad \text{Ask Depth} = \sum_{i=1}^5 \text{AskQty}_i, \quad \text{Total Depth} = \text{Bid Depth} + \text{Ask Depth}$$

### 3.2 Order Book Imbalance (OBI) & Level-Weighted Imbalance (W-OBI)
- **Standard Order Imbalance (OBI)**:
  $$\text{OBI} = \frac{\text{Bid Depth} - \text{Ask Depth}}{\text{Bid Depth} + \text{Ask Depth}} \in [-1, +1]$$
  *(-1 = 100% Sell Dominance, 0 = Balanced, +1 = 100% Buy Dominance)*
- **Level-Weighted Imbalance (W-OBI)**:
  Assigns decaying weights to deeper levels because Level 1 is immediately executable:
  $$\text{Weights}: w_1 = 1.00, \; w_2 = 0.80, \; w_3 = 0.60, \; w_4 = 0.40, \; w_5 = 0.20$$
  $$WB = \sum_{i=1}^5 w_i \cdot \text{BidQty}_i, \quad WA = \sum_{i=1}^5 w_i \cdot \text{AskQty}_i$$
  $$\text{W-OBI} = \frac{WB - WA}{WB + WA} \in [-1, +1]$$

### 3.3 Microprice & Microprice Pressure
Mid-price ignores order volume at the top level. The **Microprice** weights prices by opposite liquidity:
$$\text{Microprice} = \frac{A_1 \cdot \text{BidQty}_1 + B_1 \cdot \text{AskQty}_1}{\text{BidQty}_1 + \text{AskQty}_1}$$
$$\text{Microprice Pressure} = \text{Microprice} - P_{mid}$$
- If $\text{Microprice} > P_{mid} \implies$ upward buying pressure.
- If $\text{Microprice} < P_{mid} \implies$ downward selling pressure.

### 3.4 Aggressor Trade Flow & Trade Strength
Because raw trade feeds may not tag buyer/seller initiator, trades are classified via the **Quote Rule / Tick Rule**:
- $\text{Trade Price} \ge A_1 \implies \text{Aggressive BUY}$
- $\text{Trade Price} \le B_1 \implies \text{Aggressive SELL}$
- Trades inside the spread use the tick-direction rule ($\Delta \text{Price} > 0 \implies \text{BUY}, \Delta \text{Price} < 0 \implies \text{SELL}$).

Metrics:
- **Buy Pressure**: $\frac{V_{buy}}{V_{buy} + V_{sell}}$
- **Sell Pressure**: $\frac{V_{sell}}{V_{buy} + V_{sell}}$
- **Trade Strength**:
  $$\text{Trade Strength} = \frac{V_{buy} - V_{sell}}{V_{buy} + V_{sell}} \in [-1, +1]$$
- **Trade Intensity**: $\frac{\text{Number of Trades}}{\Delta t}$
- **Volume Intensity**: $\frac{\text{Total Traded Volume}}{\Delta t}$

### 3.5 Order Flow Imbalance (OFI) & Liquidity Dynamics
Static order book snapshots can be manipulated by order cancellations. **OFI** measures the true change in queued liquidity between consecutive snapshots $t-1$ and $t$:
$$\Delta \text{Bid} = \text{BidQty}_t - \text{BidQty}_{t-1}$$
$$\Delta \text{Ask} = \text{AskQty}_t - \text{AskQty}_{t-1}$$
$$\text{OFI} = \Delta \text{Bid} - \Delta \text{Ask}$$
$$\text{Normalized OFI} = \frac{\Delta \text{Bid} - \Delta \text{Ask}}{|\Delta \text{Bid}| + |\Delta \text{Ask}|}$$
Tracks additions vs. cancellations (liquidity replenishment vs. liquidity depletion).

### 3.6 Multi-Factor Composite Strength Score
Combines order book state and trade execution into a unified alpha signal:
$$\text{Strength Score} = w_1 \cdot (\text{W-OBI}) + w_2 \cdot (\text{Trade Strength}) + w_3 \cdot (\text{OFI}) + w_4 \cdot (\text{Microprice Pressure}_{norm}) + w_5 \cdot (\text{Trade Intensity}_{norm})$$

---

## 4. Price Targets & Strict Zero-Look-Ahead Alignment

To conduct valid econometric and machine learning research, all observations must respect causality:

### 4.1 Forward Return Horizons
For each snapshot at time $t$, future price returns are calculated:
$$R_{\tau}(t) = \frac{P_{mid}(t + \tau)}{P_{mid}(t)} - 1 \quad \text{for } \tau \in \{1\text{s}, 5\text{s}, 10\text{s}, 30\text{s}, 60\text{s}, 5\text{m}, 10\text{m}, 30\text{m}\}$$

### 4.2 Directional Classification Target
$$\text{Direction}_{\tau}(t) = \begin{cases} +1 (\text{BUY}) & \text{if } R_{\tau}(t) > +\theta \\ -1 (\text{SELL}) & \text{if } R_{\tau}(t) < -\theta \\ 0 (\text{NEUTRAL}) & \text{otherwise} \end{cases}$$
*(Threshold $\theta$ can be fixed e.g. 0.05% or dynamically volatility-adjusted).*

### 4.3 Look-Ahead Bias Prevention
$$\begin{array}{rcl}
\text{Features at time } T & \longrightarrow & \text{computed strictly using data up to } T \\
\text{Target at time } T + \tau & \longrightarrow & \text{computed using price at } T + \tau
\end{array}$$
Features are never calculated with data from the future.

---

## 5. Database Schema & Data Models

The system requires four primary relational / time-series database tables:

```sql
-- 1. Order Book Snapshot (Top 5 Levels)
CREATE TABLE order_book_snapshot (
    id BIGSERIAL PRIMARY KEY,
    instrument_token BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    bid_price_1 NUMERIC(12, 4), bid_qty_1 INT,
    bid_price_2 NUMERIC(12, 4), bid_qty_2 INT,
    bid_price_3 NUMERIC(12, 4), bid_qty_3 INT,
    bid_price_4 NUMERIC(12, 4), bid_qty_4 INT,
    bid_price_5 NUMERIC(12, 4), bid_qty_5 INT,
    ask_price_1 NUMERIC(12, 4), ask_qty_1 INT,
    ask_price_2 NUMERIC(12, 4), ask_qty_2 INT,
    ask_price_3 NUMERIC(12, 4), ask_qty_3 INT,
    ask_price_4 NUMERIC(12, 4), ask_qty_4 INT,
    ask_price_5 NUMERIC(12, 4), ask_qty_5 INT,
    mid_price NUMERIC(12, 4),
    spread NUMERIC(12, 4),
    last_traded_price NUMERIC(12, 4),
    last_traded_quantity INT,
    volume BIGINT
);

-- 2. Trade Tick Store
CREATE TABLE trade_tick (
    id BIGSERIAL PRIMARY KEY,
    instrument_token BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    price NUMERIC(12, 4) NOT NULL,
    quantity INT NOT NULL,
    side VARCHAR(10) NOT NULL, -- 'BUY', 'SELL', 'UNKNOWN'
    trade_id VARCHAR(64)
);

-- 3. Resampled Market Features Table (e.g. 100ms, 1s, 5s intervals)
CREATE TABLE market_features (
    id BIGSERIAL PRIMARY KEY,
    instrument_token BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    bid_depth INT,
    ask_depth INT,
    imbalance DOUBLE PRECISION,
    weighted_imbalance DOUBLE PRECISION,
    microprice NUMERIC(12, 4),
    microprice_pressure NUMERIC(12, 4),
    buy_volume INT,
    sell_volume INT,
    trade_strength DOUBLE PRECISION,
    trade_count INT,
    trade_intensity DOUBLE PRECISION,
    volume_intensity DOUBLE PRECISION,
    ofi DOUBLE PRECISION,
    spread NUMERIC(12, 4),
    volatility DOUBLE PRECISION
);

-- 4. Forward Price Targets Table
CREATE TABLE price_targets (
    id BIGSERIAL PRIMARY KEY,
    instrument_token BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    return_1s DOUBLE PRECISION,
    return_5s DOUBLE PRECISION,
    return_10s DOUBLE PRECISION,
    return_30s DOUBLE PRECISION,
    return_60s DOUBLE PRECISION,
    direction_1s SMALLINT,
    direction_5s SMALLINT,
    direction_10s SMALLINT,
    direction_30s SMALLINT,
    direction_60s SMALLINT
);
```

---

## 6. Analytical & Research Capabilities

The platform implements five major analytical modules:

1. **Correlation & Decay Analysis**:
   - Computes Pearson (linear) and Spearman (rank-monotonic) correlations between all features and future return horizons ($1s, 5s, 10s, 30s, 60s$).
   - Plots the **signal decay curve** to pinpoint the exact time window where order book imbalance is most predictive.

2. **Conditional & Regime Analysis**:
   - Segments data into distinct market regimes:
     - High Volatility vs. Low Volatility (ATR / Realized Volatility)
     - High Volume vs. Low Volume
     - Narrow Spread vs. Wide Spread
     - High Book Depth vs. Low Book Depth

3. **Time-of-Day Seasonality (Indian Market Hours)**:
   - Evaluates signal performance across market phases:
     - `09:15 – 09:30` (Market Open / Discovery)
     - `09:30 – 10:30` (Morning Momentum)
     - `10:30 – 12:00` (Mid-Morning Continuation)
     - `12:00 – 14:00` (Midday Consolidation / European Open)
     - `14:00 – 15:00` (Afternoon Trend)
     - `15:00 – 15:30` (Market Close / Expiry Auctions)

4. **Trade Strength $\times$ Imbalance Confluence Matrix**:
   - Constructs a $3 \times 3$ matrix (Sell, Neutral, Buy) cross-tabulating Order Book Imbalance against Executed Trade Strength.
   - Evaluates **Confluence** (e.g. Strong Buy OBI + Strong Buy Trade Strength) vs. **Conflict** (e.g. Strong Buy OBI + Strong Sell Trade Strength).

5. **Realistic Backtesting & Statistical Significance**:
   - Strict chronological walk-forward splitting: **60% Training, 20% Validation, 20% Testing** (No random shuffling of financial time-series).
   - Metrics: Directional Accuracy, Precision, Recall, ROC-AUC, Expected Return, Max Drawdown, Sharpe Ratio, Profit Factor, Hit Rate.
   - Economic friction controls: Transaction costs, exchange fees, bid/ask spread, slippage, latency simulation, and market impact.
   - Statistical test reporting: Sample size ($N$), Pearson $r$, $p$-values ($p < 0.001$), and 95% confidence intervals.

---

## 7. The 10 Core Research Questions

| Question | Investigation | Target Analysis |
|---|---|---|
| **Q1** | Does order book imbalance predict direction? | $\text{OBI} \longrightarrow \text{Future Return}$ |
| **Q2** | Does trade strength improve prediction? | $\text{OBI} + \text{Trade Strength} \longrightarrow \text{Future Return}$ |
| **Q3** | Does order flow change outperform static imbalance? | $\text{OFI vs. OBI}$ |
| **Q4** | Does microprice outperform mid-price? | $\text{Microprice Pressure} \longrightarrow \text{Future Return}$ |
| **Q5** | How quickly does the signal decay? | $1\text{s}, 5\text{s}, 10\text{s}, 30\text{s}, 60\text{s}$ persistence |
| **Q6** | Does liquidity depth change the relationship? | High Depth vs. Low Depth |
| **Q7** | Does market volatility alter predictive power? | High Volatility vs. Low Volatility |
| **Q8** | Does time-of-day affect signal reliability? | Open vs. Mid-Session vs. Close |
| **Q9** | Does multi-signal confluence improve win rate? | $\text{Strong OBI} + \text{Strong Trade Strength} + \text{Positive OFI}$ |
| **Q10**| Can the signal predict return magnitude? | Continuous Expected Return vs. Up/Down Classification |

---

## 8. Expected Final Research Output Format

The output of the system is a **statistically validated conditional rule**:

```
[CONDITION]
  WHEN:
    • Weighted OBI          > +0.40
    • Trade Strength        > +0.30
    • Normalized OFI        > +0.20
    • Trade Intensity       > 75th Percentile

[RESULT]
  THEN:
    • Probability of Positive 5-Second Return : 68.4% (vs 50.1% baseline)
    • Median 5-Second Return                  : +0.038%
    • Median 10-Second Return                 : +0.052%
    • Signal Half-Life Persistence            : 8.2 Seconds
    • Sample Size                             : 142,500 Observations
    • Statistical Significance                : p < 0.0001 (Statistically Significant)
    • Net Expected Alpha after Spread & Fees  : +0.019% (Economically Significant)
```

---

## 9. How We Will Build This with Java Spring Boot

To build this platform cleanly in Java Spring Boot:

1. **Spring Boot 3.x (Java 17 / 21)**
   - High concurrency using Virtual Threads / Project Loom or reactive pipelines.
2. **Kite Connect WebSocket Ingestion Service**
   - Resilient WebSocket connection to Zerodha Kite with automated heartbeat, reconnection, and binary packet parsing.
   - Mock/Replay data feeder for offline backtesting and simulated market sessions.
3. **In-Memory Ring Buffer / Disruptor Pipeline**
   - Zero-allocation high-speed feature calculation for Level-2 depth and tick streams.
4. **PostgreSQL & TimescaleDB / JDBC Batching**
   - High-throughput batch inserts for snapshots and ticks; queryable views for resampled intervals.
5. **Statistical & Quantitative Analytics Core**
   - Apache Commons Math / EJML for matrix correlation, linear regression, Spearman rank, and $p$-value calculations.
   - Built-in classification & regime segmentation engine.
6. **Interactive Real-Time Dashboard**
   - WebSocket streaming of computed order book state, visual depth ladders, imbalance gauges, price vs. imbalance charts, and return probability forecasts.
   - Clean REST API for querying historical statistical reports and backtesting runs.
