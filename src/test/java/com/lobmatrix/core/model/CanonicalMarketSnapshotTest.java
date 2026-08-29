package com.lobmatrix.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CanonicalMarketSnapshotTest {

    @Test
    @DisplayName("Verify defensive copying prevents external array mutation")
    void testDefensiveCopying() {
        double[] bids = {100.0, 99.0, 98.0, 97.0, 96.0};
        double[] asks = {101.0, 102.0, 103.0, 104.0, 105.0};
        long[] bidQty = {10, 20, 30, 40, 50};
        long[] askQty = {5, 15, 25, 35, 45};
        int[] bidOrders = {1, 2, 3, 4, 5};
        int[] askOrders = {1, 1, 2, 2, 3};

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "ZERODHA", 738561L, "RELIANCE",
                1_000_000_000L, 1_700_000_000_000L, 1_700_000_000L,
                100.5, 10, 500_000, 100.2, 5,
                bids, bidQty, bidOrders,
                asks, askQty, askOrders,
                BookStateTag.NORMAL
        );

        // Mutate original array
        bids[0] = 999.0;
        // Verify snapshot remains uncorrupted at 100.0
        assertThat(snapshot.bidPrices()[0]).isEqualTo(100.0);

        // Mutate array returned from getter
        double[] retrievedBids = snapshot.bidPrices();
        retrievedBids[0] = 888.0;
        assertThat(snapshot.bidPrices()[0]).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Verify mid-price, spread, and state tag calculations")
    void testCalculationsAndStateTags() {
        double[] bids = {2500.0, 2499.0};
        double[] asks = {2501.0, 2502.0};
        long[] qtys = {100, 200};
        int[] orders = {5, 10};

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 12345L, "INFY",
                100L, 200L, 300L,
                2500.5, 50, 10000, 2500.2, 2,
                bids, qtys, orders,
                asks, qtys, orders,
                CanonicalMarketSnapshot.evaluateStateTag(bids, asks)
        );

        assertThat(snapshot.midPrice()).isCloseTo(2500.50, within(0.0001));
        assertThat(snapshot.spread()).isCloseTo(1.00, within(0.0001));
        assertThat(snapshot.stateTag()).isEqualTo(BookStateTag.NORMAL);

        // Test Crossed Book detection
        double[] crossedBids = {2505.0};
        double[] crossedAsks = {2501.0};
        assertThat(CanonicalMarketSnapshot.evaluateStateTag(crossedBids, crossedAsks))
                .isEqualTo(BookStateTag.STATE_CROSSED);

        // Test Locked Book detection
        double[] lockedBids = {2501.0};
        double[] lockedAsks = {2501.0};
        assertThat(CanonicalMarketSnapshot.evaluateStateTag(lockedBids, lockedAsks))
                .isEqualTo(BookStateTag.STATE_LOCKED);
    }
}
