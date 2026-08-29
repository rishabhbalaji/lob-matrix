package com.lobmatrix.engine.math;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.state.BookAnomalyClassifier;

/**
 * High-speed mathematical calculator for Volume-Weighted Microprice and Microprice Pressure.
 */
public class MicropriceCalculator {

    /**
     * Calculates Level-1 Volume-Weighted Microprice:
     * P_micro = (Ask1 * BidQty1 + Bid1 * AskQty1) / (BidQty1 + AskQty1).
     *
     * @return Microprice, or Double.NaN if snapshot is uncomputable
     */
    public static double calculateLevel1Microprice(CanonicalMarketSnapshot snapshot) {
        if (!BookAnomalyClassifier.isComputeReady(snapshot)) {
            return Double.NaN;
        }

        double[] bidPrices = snapshot.bidPrices();
        double[] askPrices = snapshot.askPrices();
        long[] bidQty = snapshot.bidQuantities();
        long[] askQty = snapshot.askQuantities();

        if (bidPrices.length == 0 || askPrices.length == 0 || bidQty.length == 0 || askQty.length == 0) {
            return Double.NaN;
        }

        double b1 = bidPrices[0];
        double a1 = askPrices[0];
        double qb1 = bidQty[0];
        double qa1 = askQty[0];

        double totalQty = qb1 + qa1;
        if (totalQty <= 0.0) {
            return (b1 + a1) / 2.0;
        }

        return (a1 * qb1 + b1 * qa1) / totalQty;
    }

    /**
     * Calculates Microprice Pressure: P_micro - P_mid.
     * Positive = Upward pressure, Negative = Downward pressure.
     */
    public static double calculateMicropricePressure(CanonicalMarketSnapshot snapshot) {
        double micro = calculateLevel1Microprice(snapshot);
        if (Double.isNaN(micro)) {
            return Double.NaN;
        }
        return micro - snapshot.midPrice();
    }

    /**
     * Calculates Relative Microprice Pressure in Basis Points (bps):
     * ((P_micro - P_mid) / P_mid) * 10,000.
     */
    public static double calculateMicropricePressureBps(CanonicalMarketSnapshot snapshot) {
        double pressure = calculateMicropricePressure(snapshot);
        if (Double.isNaN(pressure)) {
            return Double.NaN;
        }
        double mid = snapshot.midPrice();
        if (mid <= 0.0) return 0.0;
        return (pressure / mid) * 10_000.0;
    }

    /**
     * Calculates Multi-Level Weighted Microprice across N depth levels.
     */
    public static double calculateMultiLevelMicroprice(CanonicalMarketSnapshot snapshot, double[] weights) {
        if (!BookAnomalyClassifier.isComputeReady(snapshot) || weights == null || weights.length == 0) {
            return Double.NaN;
        }

        double[] bidPrices = snapshot.bidPrices();
        double[] askPrices = snapshot.askPrices();
        long[] bidQty = snapshot.bidQuantities();
        long[] askQty = snapshot.askQuantities();

        int levels = Math.min(Math.min(bidPrices.length, askPrices.length), weights.length);
        if (levels == 0) return Double.NaN;

        double weightedNumerator = 0.0;
        double weightedDenominator = 0.0;

        for (int i = 0; i < levels; i++) {
            double w = weights[i];
            double bi = bidPrices[i];
            double ai = askPrices[i];
            double qb = bidQty[i];
            double qa = askQty[i];

            weightedNumerator += w * (ai * qb + bi * qa);
            weightedDenominator += w * (qb + qa);
        }

        if (weightedDenominator <= 0.0) {
            return snapshot.midPrice();
        }

        return weightedNumerator / weightedDenominator;
    }
}
