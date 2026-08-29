package com.lobmatrix.engine.state;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookAnomalyClassifierTest {

    @Test
    @DisplayName("Verify classification of Normal, Crossed, Locked, and Empty order books")
    void testStateClassification() {
        // Normal
        double[] bids = {100.0, 99.0};
        double[] asks = {101.0, 102.0};
        assertThat(BookAnomalyClassifier.classifyState(bids, asks)).isEqualTo(BookStateTag.NORMAL);

        // Crossed
        double[] crossedBids = {102.0, 99.0};
        double[] crossedAsks = {101.0, 103.0};
        assertThat(BookAnomalyClassifier.classifyState(crossedBids, crossedAsks)).isEqualTo(BookStateTag.STATE_CROSSED);

        // Locked
        double[] lockedBids = {101.0, 99.0};
        double[] lockedAsks = {101.0, 103.0};
        assertThat(BookAnomalyClassifier.classifyState(lockedBids, lockedAsks)).isEqualTo(BookStateTag.STATE_LOCKED);

        // Empty Side
        assertThat(BookAnomalyClassifier.classifyState(new double[0], asks)).isEqualTo(BookStateTag.STATE_EMPTY_SIDE);
        assertThat(BookAnomalyClassifier.classifyState(new double[]{0.0}, asks)).isEqualTo(BookStateTag.STATE_EMPTY_SIDE);
    }

    @Test
    @DisplayName("Verify ladder monotonicity validation for Bids and Asks")
    void testMonotonicity() {
        // Valid ladder
        double[] validBids = {2500.0, 2499.5, 2499.0, 2498.5, 2498.0};
        double[] validAsks = {2500.5, 2501.0, 2501.5, 2502.0, 2502.5};
        assertThat(BookAnomalyClassifier.isLadderMonotonic(validBids, validAsks)).isTrue();

        // Inverted Bid Ladder (B2 > B1)
        double[] badBids = {2500.0, 2505.0, 2499.0};
        assertThat(BookAnomalyClassifier.isLadderMonotonic(badBids, validAsks)).isFalse();

        // Inverted Ask Ladder (A2 < A1)
        double[] badAsks = {2500.5, 2499.0, 2502.0};
        assertThat(BookAnomalyClassifier.isLadderMonotonic(validBids, badAsks)).isFalse();
    }

    @Test
    @DisplayName("Verify isComputeReady rejects anomalous snapshots")
    void testComputeReady() {
        double[] bids = {100.0, 99.0};
        double[] asks = {101.0, 102.0};
        long[] qtys = {10, 20};
        int[] orders = {1, 2};

        CanonicalMarketSnapshot normalSnapshot = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                100.5, 10, 1000, 100.0, 2,
                bids, qtys, orders, asks, qtys, orders, BookStateTag.NORMAL
        );
        assertThat(BookAnomalyClassifier.isComputeReady(normalSnapshot)).isTrue();

        CanonicalMarketSnapshot crossedSnapshot = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                100.5, 10, 1000, 100.0, 2,
                new double[]{102.0, 99.0}, qtys, orders, asks, qtys, orders, BookStateTag.STATE_CROSSED
        );
        assertThat(BookAnomalyClassifier.isComputeReady(crossedSnapshot)).isFalse();
    }
}
