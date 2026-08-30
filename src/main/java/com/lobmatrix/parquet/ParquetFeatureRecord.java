package com.lobmatrix.parquet;

import com.lobmatrix.engine.target.DirectionalLabel;

/**
 * Standard flat 40+ column canonical feature vector for high-speed Parquet serialization.
 */
public record ParquetFeatureRecord(
        // Clocks & Identifiers
        long gridSequence,
        long gridNanos,
        long deltaIntervalNanos,
        long instrumentToken,
        String symbol,
        double snapshotAgeMs,

        // Core LOB Price & Spread
        double bestBidPrice,
        double bestAskPrice,
        double midPrice,
        double spread,
        double relativeSpreadBps,
        double ltp,
        long cumulativeVolume,
        double dayVwap,

        // Microstructure Alpha Signals
        double level1OBI,
        double totalOBI,
        double weightedOBILinear,
        double weightedOBIExp,
        double level1Microprice,
        double micropricePressure,
        double micropricePressureBps,
        double multiLevelMicroprice,
        double level1OFI,
        double multiLevelOFIUniform,
        double multiLevelOFIExp,
        double tradeStrength,
        double buyPressure,
        double sellPressure,

        // Forward Mid-Price Return Targets
        double return1s,
        double return5s,
        double return10s,
        double return30s,
        double return60s,

        // Tri-Class Directional Labels {-1, 0, +1}
        int label1s,
        int label5s,
        int label10s,
        int label30s,
        int label60s,

        // Net Executable Return Targets (deducting spread crossing & Indian statutory fees)
        double execLongReturn1s,
        double execLongReturn5s,
        double execLongReturn10s,
        double execLongReturn30s,
        double execLongReturn60s
) {}
