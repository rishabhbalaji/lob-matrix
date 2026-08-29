package com.lobmatrix.engine.math;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MultiLevelOFICalculatorTest {

    @Test
    @DisplayName("Verify Cont-Kukanov-Stoikov Level-1 OFI price shift conditions")
    void testLevel1OFIConditions() {
        // State t0: Bid 100 (qty 500), Ask 101 (qty 400)
        CanonicalMarketSnapshot t0 = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1000L, 1000L, 1700000000L,
                100.5, 10, 1000, 100.0, 1,
                new double[]{100.0}, new long[]{500}, new int[]{1},
                new double[]{101.0}, new long[]{400}, new int[]{1},
                BookStateTag.NORMAL
        );

        // Case 1: Same prices, but 200 bids added (500 -> 700) and 100 asks cancelled (400 -> 300)
        // deltaBid = +200, deltaAsk = -100
        // OFI = 200 - (-100) = +300 (Strong buy flow)
        CanonicalMarketSnapshot t1 = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 2000L, 2000L, 1700000001L,
                100.5, 10, 1000, 100.0, 1,
                new double[]{100.0}, new long[]{700}, new int[]{1},
                new double[]{101.0}, new long[]{300}, new int[]{1},
                BookStateTag.NORMAL
        );
        double ofi1 = MultiLevelOFICalculator.calculateLevel1OFI(t0, t1);
        assertThat(ofi1).isCloseTo(300.0, within(0.0001));

        // Case 2: Bid price stepped UP (100 -> 100.5 with qty 350)
        // Ask stayed at 101 (qty 300)
        // deltaBid = +350, deltaAsk = (300 - 300) = 0
        // OFI = +350
        CanonicalMarketSnapshot t2 = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 3000L, 3000L, 1700000002L,
                100.5, 10, 1000, 100.0, 1,
                new double[]{100.5}, new long[]{350}, new int[]{1},
                new double[]{101.0}, new long[]{300}, new int[]{1},
                BookStateTag.NORMAL
        );
        double ofi2 = MultiLevelOFICalculator.calculateLevel1OFI(t1, t2);
        assertThat(ofi2).isCloseTo(350.0, within(0.0001));
    }

    @Test
    @DisplayName("Verify Multi-Level OFI across multiple depth levels")
    void testMultiLevelOFI() {
        double[] bids0 = {2500.0, 2499.0};
        double[] asks0 = {2501.0, 2502.0};
        long[] bidQty0 = {100, 200};
        long[] askQty0 = {100, 200};
        int[] orders = {1, 1};

        CanonicalMarketSnapshot t0 = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1000L, 1000L, 1700000000L,
                2500.5, 10, 1000, 2500.0, 2,
                bids0, bidQty0, orders, asks0, askQty0, orders, BookStateTag.NORMAL
        );

        // Level 1: Bid +50, Ask 0 -> Level 1 OFI = +50
        // Level 2: Bid +100, Ask 0 -> Level 2 OFI = +100
        long[] bidQty1 = {150, 300};
        long[] askQty1 = {100, 200};
        CanonicalMarketSnapshot t1 = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 2000L, 2000L, 1700000001L,
                2500.5, 10, 1000, 2500.0, 2,
                bids0, bidQty1, orders, asks0, askQty1, orders, BookStateTag.NORMAL
        );

        // Uniform Weights: [0.5, 0.5]
        // ML-OFI = (0.5 * 50) + (0.5 * 100) = 25 + 50 = +75.0
        double mlOfi = MultiLevelOFICalculator.calculateMultiLevelOFI(t0, t1, new double[]{0.5, 0.5});
        assertThat(mlOfi).isCloseTo(75.0, within(0.0001));
    }
}
