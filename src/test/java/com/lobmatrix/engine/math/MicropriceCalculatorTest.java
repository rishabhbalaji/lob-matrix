package com.lobmatrix.engine.math;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MicropriceCalculatorTest {

    @Test
    @DisplayName("Verify Level-1 Microprice shifts toward Ask when Bid queue is heavy")
    void testMicropriceGravitationalShift() {
        double[] bids = {100.0};
        double[] asks = {101.0};
        // 900 Bids vs 100 Asks (90% buyer queue)
        long[] bidQty = {900};
        long[] askQty = {100};
        int[] orders = {1};

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                100.5, 10, 1000, 100.0, 1,
                bids, bidQty, orders, asks, askQty, orders, BookStateTag.NORMAL
        );

        // Mid-price = (100 + 101) / 2 = 100.50
        assertThat(snapshot.midPrice()).isCloseTo(100.50, within(0.0001));

        // Microprice = (101 * 900 + 100 * 100) / 1000 = (90900 + 10000) / 1000 = 100.90
        double micro = MicropriceCalculator.calculateLevel1Microprice(snapshot);
        assertThat(micro).isCloseTo(100.90, within(0.0001));

        // Pressure = 100.90 - 100.50 = +0.40 (Upward pressure)
        double pressure = MicropriceCalculator.calculateMicropricePressure(snapshot);
        assertThat(pressure).isCloseTo(0.40, within(0.0001));

        // Relative Pressure in bps = (0.40 / 100.50) * 10000 = ~39.80 bps
        double pressureBps = MicropriceCalculator.calculateMicropricePressureBps(snapshot);
        assertThat(pressureBps).isCloseTo((0.40 / 100.50) * 10_000.0, within(0.01));
    }

    @Test
    @DisplayName("Verify Multi-Level Weighted Microprice calculation")
    void testMultiLevelMicroprice() {
        double[] bids = {2500.0, 2499.0};
        double[] asks = {2501.0, 2502.0};
        long[] bidQty = {500, 1000};
        long[] askQty = {500, 1000};
        int[] orders = {1, 1};

        CanonicalMarketSnapshot balancedSnapshot = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                2500.5, 10, 1000, 2500.0, 2,
                bids, bidQty, orders, asks, askQty, orders, BookStateTag.NORMAL
        );

        // Symmetric book with identical bid/ask queues -> Microprice == Mid-Price
        double multiMicro = MicropriceCalculator.calculateMultiLevelMicroprice(balancedSnapshot, new double[]{1.0, 0.8});
        assertThat(multiMicro).isCloseTo(2500.50, within(0.0001));
    }

    @Test
    @DisplayName("Verify NaN return on crossed/invalid books")
    void testAnomalousBookHandling() {
        double[] crossedBids = {102.0};
        double[] crossedAsks = {100.0};
        long[] qty = {100};
        int[] orders = {1};

        CanonicalMarketSnapshot crossedSnapshot = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                101.0, 10, 1000, 100.0, 1,
                crossedBids, qty, orders, crossedAsks, qty, orders, BookStateTag.STATE_CROSSED
        );

        assertThat(MicropriceCalculator.calculateLevel1Microprice(crossedSnapshot)).isNaN();
        assertThat(MicropriceCalculator.calculateMicropricePressure(crossedSnapshot)).isNaN();
    }
}
