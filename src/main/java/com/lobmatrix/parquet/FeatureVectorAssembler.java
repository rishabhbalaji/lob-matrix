package com.lobmatrix.parquet;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.math.*;
import com.lobmatrix.engine.resample.ResampledGridPoint;
import com.lobmatrix.engine.target.*;

/**
 * High-speed assembler converting resampled LOB states and mathematical engines into canonical Parquet feature records.
 */
public class FeatureVectorAssembler {

    private static final double[] TOP5_EXP_WEIGHTS = OrderBookImbalanceCalculator.generateExponentialWeights(5, 0.5);
    private static final double[] TOP5_UNIFORM_OFI_WEIGHTS = {0.2, 0.2, 0.2, 0.2, 0.2};
    private static final double[] TOP5_EXP_OFI_WEIGHTS = {1.0, 0.6065, 0.3678, 0.2231, 0.1353};

    public static ParquetFeatureRecord assemble(
            ResampledGridPoint gridPoint,
            CanonicalMarketSnapshot prevSnapshot,
            TradeStrengthClassifier tradeClassifier,
            ForwardReturnTargets forwardTargets,
            CostSchedule costSchedule,
            double sigma1s
    ) {
        CanonicalMarketSnapshot snap = gridPoint.snapshot();
        double mid = snap.midPrice();
        double spread = snap.spread();
        double relSpreadBps = mid > 0 ? (spread / mid) * 10_000.0 : 0.0;

        double b1 = snap.bidPrices().length > 0 ? snap.bidPrices()[0] : 0.0;
        double a1 = snap.askPrices().length > 0 ? snap.askPrices()[0] : 0.0;

        // Microstructure math
        double l1Obi = OrderBookImbalanceCalculator.calculateLevel1OBI(snap);
        double totObi = OrderBookImbalanceCalculator.calculateTotalOBI(snap);
        double wObiLin = OrderBookImbalanceCalculator.calculateWeightedOBI(snap, OrderBookImbalanceCalculator.TOP5_LINEAR_WEIGHTS);
        double wObiExp = OrderBookImbalanceCalculator.calculateWeightedOBI(snap, TOP5_EXP_WEIGHTS);

        double micro = MicropriceCalculator.calculateLevel1Microprice(snap);
        double microPressure = MicropriceCalculator.calculateMicropricePressure(snap);
        double microPressureBps = MicropriceCalculator.calculateMicropricePressureBps(snap);
        double mlMicro = MicropriceCalculator.calculateMultiLevelMicroprice(snap, OrderBookImbalanceCalculator.TOP5_LINEAR_WEIGHTS);

        double l1Ofi = prevSnapshot != null ? MultiLevelOFICalculator.calculateLevel1OFI(prevSnapshot, snap) : 0.0;
        double mlOfiUniform = prevSnapshot != null ? MultiLevelOFICalculator.calculateMultiLevelOFI(prevSnapshot, snap, TOP5_UNIFORM_OFI_WEIGHTS) : 0.0;
        double mlOfiExp = prevSnapshot != null ? MultiLevelOFICalculator.calculateMultiLevelOFI(prevSnapshot, snap, TOP5_EXP_OFI_WEIGHTS) : 0.0;

        double tradeStrength = tradeClassifier != null ? tradeClassifier.calculateTradeStrength() : 0.0;
        double buyPressure = tradeClassifier != null ? tradeClassifier.calculateBuyPressure() : 0.5;
        double sellPressure = tradeClassifier != null ? tradeClassifier.calculateSellPressure() : 0.5;

        // Forward returns
        double r1s = forwardTargets != null ? forwardTargets.return1s() : Double.NaN;
        double r5s = forwardTargets != null ? forwardTargets.return5s() : Double.NaN;
        double r10s = forwardTargets != null ? forwardTargets.return10s() : Double.NaN;
        double r30s = forwardTargets != null ? forwardTargets.return30s() : Double.NaN;
        double r60s = forwardTargets != null ? forwardTargets.return60s() : Double.NaN;

        // Directional labels using volatility thresholds
        long deltaNanos = gridPoint.deltaIntervalNanos();
        double th1s = VolatilityThresholdClassifier.computeDefaultThreshold(sigma1s, deltaNanos, ForwardReturnTargets.TAU_1S_NANOS, spread, mid);
        double th5s = VolatilityThresholdClassifier.computeDefaultThreshold(sigma1s, deltaNanos, ForwardReturnTargets.TAU_5S_NANOS, spread, mid);
        double th10s = VolatilityThresholdClassifier.computeDefaultThreshold(sigma1s, deltaNanos, ForwardReturnTargets.TAU_10S_NANOS, spread, mid);
        double th30s = VolatilityThresholdClassifier.computeDefaultThreshold(sigma1s, deltaNanos, ForwardReturnTargets.TAU_30S_NANOS, spread, mid);
        double th60s = VolatilityThresholdClassifier.computeDefaultThreshold(sigma1s, deltaNanos, ForwardReturnTargets.TAU_60S_NANOS, spread, mid);

        int y1s = VolatilityThresholdClassifier.classifyDirection(r1s, th1s).getValue();
        int y5s = VolatilityThresholdClassifier.classifyDirection(r5s, th5s).getValue();
        int y10s = VolatilityThresholdClassifier.classifyDirection(r10s, th10s).getValue();
        int y30s = VolatilityThresholdClassifier.classifyDirection(r30s, th30s).getValue();
        int y60s = VolatilityThresholdClassifier.classifyDirection(r60s, th60s).getValue();

        // Executable returns (deducting spread crossing and Indian transaction costs)
        CostSchedule sched = costSchedule != null ? costSchedule : CostModelRepository.getDefaultSchedule();
        double tradeVal = 100_000.0;
        double exec1s = Double.isNaN(r1s) ? Double.NaN : ExecutableReturnCalculator.calculateLongExecutableReturn(snap, mid * Math.exp(r1s), sched, tradeVal);
        double exec5s = Double.isNaN(r5s) ? Double.NaN : ExecutableReturnCalculator.calculateLongExecutableReturn(snap, mid * Math.exp(r5s), sched, tradeVal);
        double exec10s = Double.isNaN(r10s) ? Double.NaN : ExecutableReturnCalculator.calculateLongExecutableReturn(snap, mid * Math.exp(r10s), sched, tradeVal);
        double exec30s = Double.isNaN(r30s) ? Double.NaN : ExecutableReturnCalculator.calculateLongExecutableReturn(snap, mid * Math.exp(r30s), sched, tradeVal);
        double exec60s = Double.isNaN(r60s) ? Double.NaN : ExecutableReturnCalculator.calculateLongExecutableReturn(snap, mid * Math.exp(r60s), sched, tradeVal);

        return new ParquetFeatureRecord(
                gridPoint.gridSequence(),
                gridPoint.gridNanos(),
                deltaNanos,
                snap.instrumentToken(),
                snap.symbol(),
                gridPoint.snapshotAgeMs(),
                b1, a1, mid, spread, relSpreadBps, snap.ltp(), snap.cumulativeVolume(), snap.dayVwap(),
                l1Obi, totObi, wObiLin, wObiExp, micro, microPressure, microPressureBps, mlMicro,
                l1Ofi, mlOfiUniform, mlOfiExp, tradeStrength, buyPressure, sellPressure,
                r1s, r5s, r10s, r30s, r60s,
                y1s, y5s, y10s, y30s, y60s,
                exec1s, exec5s, exec10s, exec30s, exec60s
        );
    }
}
