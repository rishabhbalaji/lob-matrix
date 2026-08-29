package com.lobmatrix.engine.math;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OrderBookImbalanceCalculatorTest {

    @Test
    @DisplayName("Verify Level-1 and Total Order Book Imbalance calculations")
    void testBasicOBICalculations() {
        double[] bids = {100.0, 99.0};
        double[] asks = {101.0, 102.0};
        long[] bidQty = {300, 700}; // Total = 1000
        long[] askQty = {100, 300}; // Total = 400
        int[] orders = {1, 1};

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                100.5, 10, 1000, 100.0, 2,
                bids, bidQty, orders, asks, askQty, orders, BookStateTag.NORMAL
        );

        // Level-1 OBI = (300 - 100) / (300 + 100) = 200 / 400 = +0.50
        double obi1 = OrderBookImbalanceCalculator.calculateLevel1OBI(snapshot);
        assertThat(obi1).isCloseTo(0.50, within(0.0001));

        // Total OBI = (1000 - 400) / (1000 + 400) = 600 / 1400 = 0.42857
        double obiTotal = OrderBookImbalanceCalculator.calculateTotalOBI(snapshot);
        assertThat(obiTotal).isCloseTo(600.0 / 1400.0, within(0.0001));
    }

    @Test
    @DisplayName("Verify Level-Weighted W-OBI with linear and exponential decay weights")
    void testWeightedOBICalculations() {
        double[] bids = {100.0, 99.0, 98.0, 97.0, 96.0};
        double[] asks = {101.0, 102.0, 103.0, 104.0, 105.0};
        // Heavy bids at back (Level 5), but light at front counter (Level 1)
        long[] bidQty = {100, 100, 100, 100, 1000};
        long[] askQty = {500, 100, 100, 100, 100};
        int[] orders = {1, 1, 1, 1, 1};

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                100.5, 10, 1000, 100.0, 5,
                bids, bidQty, orders, asks, askQty, orders, BookStateTag.NORMAL
        );

        // Standard Linear Weights: [1.0, 0.8, 0.6, 0.4, 0.2]
        // WB = (1.0*100) + (0.8*100) + (0.6*100) + (0.4*100) + (0.2*1000) = 100 + 80 + 60 + 40 + 200 = 480
        // WA = (1.0*500) + (0.8*100) + (0.6*100) + (0.4*100) + (0.2*100)  = 500 + 80 + 60 + 40 + 20  = 700
        // W-OBI = (480 - 700) / (480 + 700) = -220 / 1180 = -0.18644
        double wobi = OrderBookImbalanceCalculator.calculateWeightedOBI(snapshot, OrderBookImbalanceCalculator.TOP5_LINEAR_WEIGHTS);
        assertThat(wobi).isCloseTo(-220.0 / 1180.0, within(0.0001));

        // Total unweighted OBI would have shown positive (+1400 vs 900 = +0.217)
        // But W-OBI correctly captures immediate selling pressure at front counter!
        assertThat(wobi).isNegative();
    }

    @Test
    @DisplayName("Verify NaN return on crossed/corrupted snapshot")
    void testAnomalyHandling() {
        double[] crossedBids = {102.0, 99.0};
        double[] crossedAsks = {101.0, 103.0};
        long[] qty = {100, 100};
        int[] orders = {1, 1};

        CanonicalMarketSnapshot crossedSnapshot = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                100.5, 10, 1000, 100.0, 2,
                crossedBids, qty, orders, crossedAsks, qty, orders, BookStateTag.STATE_CROSSED
        );

        assertThat(OrderBookImbalanceCalculator.calculateLevel1OBI(crossedSnapshot)).isNaN();
        assertThat(OrderBookImbalanceCalculator.calculateWeightedOBI(crossedSnapshot, new double[]{1.0, 0.8})).isNaN();
    }
}
