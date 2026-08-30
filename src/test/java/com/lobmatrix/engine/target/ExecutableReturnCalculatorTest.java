package com.lobmatrix.engine.target;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExecutableReturnCalculatorTest {

    @Test
    @DisplayName("Verify Versioned Indian Regulatory Cost Schedule (Oct 2024 revision)")
    void testRegulatoryCostSchedule() {
        CostSchedule schedule = CostModelRepository.getSchedule(LocalDate.of(2024, 11, 1));
        assertThat(schedule.scheduleName()).isEqualTo("NSE_EQUITY_OCT_2024");

        // 100,000 INR trade value
        double effectiveCostRate = schedule.calculateEffectiveRoundTripCostRate(100_000.0);

        // STT (25 INR) + Brokerage (40 INR) + NSE (5.94 INR) + GST (8.27 INR) + Stamp (3 INR) + SEBI (~0.20 INR)
        // Total ~ 82.41 INR / 100,000 = ~ 0.000824 (8.24 bps)
        assertThat(effectiveCostRate).isCloseTo(0.000824, within(0.000100));
    }

    @Test
    @DisplayName("Verify Spread-Crossed Executable Net Return for Long and Short trades")
    void testExecutableReturns() {
        CostSchedule schedule = CostModelRepository.getDefaultSchedule();

        double[] bids = {1000.0};
        double[] asks = {1001.0}; // 1.0 spread (10 bps half-spread friction)
        long[] qty = {100};
        int[] orders = {1};

        CanonicalMarketSnapshot entry = new CanonicalMarketSnapshot(
                "MOCK", 738561L, "RELIANCE",
                1000L, 1000L, 1700000000L,
                1000.5, 10, 1000, 1000.0, 1,
                bids, qty, orders, asks, qty, orders, BookStateTag.NORMAL
        );

        // Long Trade: Entry Buy at Ask1 (1001.0).
        // If price moves up and forward Bid1 reaches 1003.0:
        // Gross = (1003.0 - 1001.0) / 1001.0 = +2.0 / 1001.0 = +0.001998 (+19.98 bps)
        // Net = Gross - CostRate (~8.24 bps)
        double forwardBid = 1003.0;
        double netLongReturn = ExecutableReturnCalculator.calculateLongExecutableReturn(entry, forwardBid, schedule, 100_000.0);

        double costRate = schedule.calculateEffectiveRoundTripCostRate(100_000.0);
        double expectedGross = (1003.0 - 1001.0) / 1001.0;
        assertThat(netLongReturn).isCloseTo(expectedGross - costRate, within(0.00001));

        // Short Trade: Entry Sell at Bid1 (1000.0).
        // Forward Ask1 drops to 998.0:
        // Gross = (1000.0 - 998.0) / 1000.0 = +2.0 / 1000.0 = +0.002000 (+20.0 bps)
        double forwardAsk = 998.0;
        double netShortReturn = ExecutableReturnCalculator.calculateShortExecutableReturn(entry, forwardAsk, schedule, 100_000.0);
        double expectedShortGross = (1000.0 - 998.0) / 1000.0;
        assertThat(netShortReturn).isCloseTo(expectedShortGross - costRate, within(0.00001));
    }
}
