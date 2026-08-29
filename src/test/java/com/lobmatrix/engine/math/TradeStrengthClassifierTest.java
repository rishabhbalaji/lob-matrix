package com.lobmatrix.engine.math;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TradeStrengthClassifierTest {

    @Test
    @DisplayName("Verify Lee-Ready Quote Rule and Tick Rule classification")
    void testLeeReadyClassification() {
        TradeStrengthClassifier classifier = new TradeStrengthClassifier();
        double bestBid = 100.0;
        double bestAsk = 101.0;

        // 1. Trade at or above Ask -> BUY
        assertThat(classifier.classifyTrade(101.0, bestBid, bestAsk)).isEqualTo(AggressorSide.BUY);
        assertThat(classifier.classifyTrade(101.5, bestBid, bestAsk)).isEqualTo(AggressorSide.BUY);

        // 2. Trade at or below Bid -> SELL
        assertThat(classifier.classifyTrade(100.0, bestBid, bestAsk)).isEqualTo(AggressorSide.SELL);
        assertThat(classifier.classifyTrade(99.5, bestBid, bestAsk)).isEqualTo(AggressorSide.SELL);

        // 3. Trade inside spread (100.50):
        // First tick at 100.50 with prev at 99.50 -> Uptick -> BUY
        assertThat(classifier.classifyTrade(100.50, bestBid, bestAsk)).isEqualTo(AggressorSide.BUY);

        // Second tick at 100.25 (prev was 100.50) -> Downtick -> SELL
        assertThat(classifier.classifyTrade(100.25, bestBid, bestAsk)).isEqualTo(AggressorSide.SELL);

        // Third tick at 100.25 (prev was 100.25) -> Zero-tick -> Carry forward SELL
        assertThat(classifier.classifyTrade(100.25, bestBid, bestAsk)).isEqualTo(AggressorSide.SELL);
    }

    @Test
    @DisplayName("Verify Trade Strength and Buy/Sell Pressure calculations")
    void testTradeStrengthCalculations() {
        TradeStrengthClassifier classifier = new TradeStrengthClassifier();

        double[] bids = {100.0};
        double[] asks = {101.0};
        long[] qty = {100};
        int[] orders = {1};

        // Snapshot with LTP = 101.0 (Buy)
        CanonicalMarketSnapshot buySnap = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                101.0, 75, 1000, 100.0, 1,
                bids, qty, orders, asks, qty, orders, BookStateTag.NORMAL
        );
        classifier.recordTrade(buySnap, 75); // 75 buy shares

        // Snapshot with LTP = 100.0 (Sell)
        CanonicalMarketSnapshot sellSnap = new CanonicalMarketSnapshot(
                "MOCK", 1L, "TEST", 1L, 2L, 3L,
                100.0, 25, 1025, 100.0, 1,
                bids, qty, orders, asks, qty, orders, BookStateTag.NORMAL
        );
        classifier.recordTrade(sellSnap, 25); // 25 sell shares

        // Total Buy = 75, Total Sell = 25, Total = 100
        // Trade Strength = (75 - 25) / 100 = +0.50
        assertThat(classifier.calculateTradeStrength()).isCloseTo(0.50, within(0.0001));

        // Buy Pressure = 75 / 100 = 0.75 (75%)
        assertThat(classifier.calculateBuyPressure()).isCloseTo(0.75, within(0.0001));

        // Sell Pressure = 25 / 100 = 0.25 (25%)
        assertThat(classifier.calculateSellPressure()).isCloseTo(0.25, within(0.0001));

        // Buy Pressure + Sell Pressure == 1.0
        assertThat(classifier.calculateBuyPressure() + classifier.calculateSellPressure()).isCloseTo(1.0, within(0.0001));
    }
}
