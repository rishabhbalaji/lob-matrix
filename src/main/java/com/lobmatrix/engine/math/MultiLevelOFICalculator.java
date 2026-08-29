package com.lobmatrix.engine.math;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.state.BookAnomalyClassifier;

/**
 * High-speed mathematical calculator for Level-1 and Multi-Level Order Flow Imbalance (OFI)
 * using the Cont-Kukanov-Stoikov (2014) formulation.
 */
public class MultiLevelOFICalculator {

    /**
     * Calculates Level-1 OFI between a previous snapshot and the current snapshot.
     *
     * @param prev Previous CanonicalMarketSnapshot at t_{k-1}
     * @param curr Current CanonicalMarketSnapshot at t_k
     * @return Level-1 OFI in shares, or Double.NaN if either snapshot is invalid
     */
    public static double calculateLevel1OFI(CanonicalMarketSnapshot prev, CanonicalMarketSnapshot curr) {
        if (!BookAnomalyClassifier.isComputeReady(prev) || !BookAnomalyClassifier.isComputeReady(curr)) {
            return Double.NaN;
        }

        double pb = prev.bidPrices()[0];
        double cb = curr.bidPrices()[0];
        long pqb = prev.bidQuantities()[0];
        long cqb = curr.bidQuantities()[0];

        double deltaBidFlow;
        if (cb > pb) {
            deltaBidFlow = cqb; // Price stepped up: new bid queue
        } else if (Double.compare(cb, pb) == 0) {
            deltaBidFlow = cqb - pqb; // Same price: net change
        } else {
            deltaBidFlow = -pqb; // Price dropped: old bid consumed/cancelled
        }

        double pa = prev.askPrices()[0];
        double ca = curr.askPrices()[0];
        long pqa = prev.askQuantities()[0];
        long cqa = curr.askQuantities()[0];

        double deltaAskFlow;
        if (ca < pa) {
            deltaAskFlow = cqa; // Price stepped down: new ask queue
        } else if (Double.compare(ca, pa) == 0) {
            deltaAskFlow = cqa - pqa; // Same price: net change
        } else {
            deltaAskFlow = -pqa; // Price stepped up: old ask consumed/cancelled
        }

        return deltaBidFlow - deltaAskFlow;
    }

    /**
     * Calculates Multi-Level OFI across N depth levels with weight vector w.
     *
     * @param prev Previous CanonicalMarketSnapshot
     * @param curr Current CanonicalMarketSnapshot
     * @param weights Weight array matching depth levels
     * @return Multi-Level OFI
     */
    public static double calculateMultiLevelOFI(CanonicalMarketSnapshot prev, CanonicalMarketSnapshot curr, double[] weights) {
        if (!BookAnomalyClassifier.isComputeReady(prev) || !BookAnomalyClassifier.isComputeReady(curr) 
                || weights == null || weights.length == 0) {
            return Double.NaN;
        }

        int levels = Math.min(Math.min(prev.depthLevels(), curr.depthLevels()), weights.length);
        if (levels == 0) return Double.NaN;

        double totalWeightedOFI = 0.0;

        double[] prevBids = prev.bidPrices();
        double[] currBids = curr.bidPrices();
        long[] prevBidQtys = prev.bidQuantities();
        long[] currBidQtys = curr.bidQuantities();

        double[] prevAsks = prev.askPrices();
        double[] currAsks = curr.askPrices();
        long[] prevAskQtys = prev.askQuantities();
        long[] currAskQtys = curr.askQuantities();

        for (int i = 0; i < levels; i++) {
            double w = weights[i];

            // Bid flow at level i
            double pb = prevBids[i];
            double cb = currBids[i];
            long pqb = prevBidQtys[i];
            long cqb = currBidQtys[i];

            double deltaBid;
            if (cb > pb) {
                deltaBid = cqb;
            } else if (Double.compare(cb, pb) == 0) {
                deltaBid = cqb - pqb;
            } else {
                deltaBid = -pqb;
            }

            // Ask flow at level i
            double pa = prevAsks[i];
            double ca = currAsks[i];
            long pqa = prevAskQtys[i];
            long cqa = currAskQtys[i];

            double deltaAsk;
            if (ca < pa) {
                deltaAsk = cqa;
            } else if (Double.compare(ca, pa) == 0) {
                deltaAsk = cqa - pqa;
            } else {
                deltaAsk = -pqa;
            }

            totalWeightedOFI += w * (deltaBid - deltaAsk);
        }

        return totalWeightedOFI;
    }
}
