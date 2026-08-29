package com.lobmatrix.engine.state;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;

/**
 * High-performance microstructure anomaly classifier and sanity validator for order books.
 */
public class BookAnomalyClassifier {

    /**
     * Evaluates order book state with deep validation of depth ladders.
     */
    public static BookStateTag classifyState(double[] bidPrices, double[] askPrices) {
        if (bidPrices == null || askPrices == null || bidPrices.length == 0 || askPrices.length == 0) {
            return BookStateTag.STATE_EMPTY_SIDE;
        }

        double bestBid = bidPrices[0];
        double bestAsk = askPrices[0];

        // Zero or negative price checks
        if (bestBid <= 0.0 || bestAsk <= 0.0) {
            return BookStateTag.STATE_EMPTY_SIDE;
        }

        if (bestBid > bestAsk) {
            return BookStateTag.STATE_CROSSED;
        } else if (Double.compare(bestBid, bestAsk) == 0) {
            return BookStateTag.STATE_LOCKED;
        }

        return BookStateTag.NORMAL;
    }

    /**
     * Verifies that the order book depth ladder satisfies strict price monotonicity.
     * Bids must strictly descend (B1 > B2 > B3 > ...).
     * Asks must strictly ascend (A1 < A2 < A3 < ...).
     *
     * @return true if ladder is monotonic and valid
     */
    public static boolean isLadderMonotonic(double[] bidPrices, double[] askPrices) {
        if (bidPrices == null || askPrices == null) {
            return false;
        }

        // Validate descending bids
        for (int i = 0; i < bidPrices.length - 1; i++) {
            if (bidPrices[i] > 0 && bidPrices[i + 1] > 0) {
                if (bidPrices[i] <= bidPrices[i + 1]) {
                    return false; // Non-monotonic bid ladder
                }
            }
        }

        // Validate ascending asks
        for (int i = 0; i < askPrices.length - 1; i++) {
            if (askPrices[i] > 0 && askPrices[i + 1] > 0) {
                if (askPrices[i] >= askPrices[i + 1]) {
                    return false; // Non-monotonic ask ladder
                }
            }
        }

        return true;
    }

    /**
     * Returns true if the order book snapshot is mathematically sound for feature calculation.
     */
    public static boolean isComputeReady(CanonicalMarketSnapshot snapshot) {
        if (snapshot == null) return false;
        if (snapshot.stateTag() != BookStateTag.NORMAL) return false;
        return isLadderMonotonic(snapshot.bidPrices(), snapshot.askPrices());
    }
}
