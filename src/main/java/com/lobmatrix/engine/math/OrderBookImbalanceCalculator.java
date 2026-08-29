package com.lobmatrix.engine.math;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.state.BookAnomalyClassifier;

import java.util.Arrays;

/**
 * High-speed mathematical calculator for Standard and Level-Weighted Order Book Imbalance (OBI).
 */
public class OrderBookImbalanceCalculator {

    // Standard linear decay weights for Top-5 depth: [1.0, 0.8, 0.6, 0.4, 0.2]
    public static final double[] TOP5_LINEAR_WEIGHTS = {1.0, 0.8, 0.6, 0.4, 0.2};

    /**
     * Calculates Top-of-Book (Level 1) Imbalance: (B1 - A1) / (B1 + A1).
     *
     * @return OBI in range [-1.0, +1.0], or Double.NaN if invalid/empty
     */
    public static double calculateLevel1OBI(CanonicalMarketSnapshot snapshot) {
        if (!BookAnomalyClassifier.isComputeReady(snapshot)) {
            return Double.NaN;
        }

        long[] bidQty = snapshot.bidQuantities();
        long[] askQty = snapshot.askQuantities();

        if (bidQty.length == 0 || askQty.length == 0) {
            return Double.NaN;
        }

        double b1 = bidQty[0];
        double a1 = askQty[0];
        double denominator = b1 + a1;

        if (denominator <= 0.0) {
            return 0.0;
        }

        return (b1 - a1) / denominator;
    }

    /**
     * Calculates Total Cumulative Depth Imbalance across all available levels.
     */
    public static double calculateTotalOBI(CanonicalMarketSnapshot snapshot) {
        if (!BookAnomalyClassifier.isComputeReady(snapshot)) {
            return Double.NaN;
        }

        long[] bidQty = snapshot.bidQuantities();
        long[] askQty = snapshot.askQuantities();

        double totalBids = 0.0;
        for (long q : bidQty) totalBids += q;

        double totalAsks = 0.0;
        for (long q : askQty) totalAsks += q;

        double denominator = totalBids + totalAsks;
        if (denominator <= 0.0) {
            return 0.0;
        }

        return (totalBids - totalAsks) / denominator;
    }

    /**
     * Calculates Level-Weighted Imbalance (W-OBI) using custom decaying weights.
     *
     * @param snapshot CanonicalMarketSnapshot
     * @param weights Array of weights matching depth levels (e.g. TOP5_LINEAR_WEIGHTS)
     * @return Weighted OBI in range [-1.0, +1.0]
     */
    public static double calculateWeightedOBI(CanonicalMarketSnapshot snapshot, double[] weights) {
        if (!BookAnomalyClassifier.isComputeReady(snapshot) || weights == null || weights.length == 0) {
            return Double.NaN;
        }

        long[] bidQty = snapshot.bidQuantities();
        long[] askQty = snapshot.askQuantities();

        int levels = Math.min(Math.min(bidQty.length, askQty.length), weights.length);
        if (levels == 0) {
            return Double.NaN;
        }

        double weightedBids = 0.0;
        double weightedAsks = 0.0;

        for (int i = 0; i < levels; i++) {
            double w = weights[i];
            weightedBids += (w * bidQty[i]);
            weightedAsks += (w * askQty[i]);
        }

        double denominator = weightedBids + weightedAsks;
        if (denominator <= 0.0) {
            return 0.0;
        }

        return (weightedBids - weightedAsks) / denominator;
    }

    /**
     * Generates exponential decay weights vector for N levels: w_i = exp(-lambda * i).
     */
    public static double[] generateExponentialWeights(int depthLevels, double lambda) {
        if (depthLevels <= 0) return new double[0];
        double[] weights = new double[depthLevels];
        for (int i = 0; i < depthLevels; i++) {
            weights[i] = Math.exp(-lambda * i);
        }
        return weights;
    }
}
