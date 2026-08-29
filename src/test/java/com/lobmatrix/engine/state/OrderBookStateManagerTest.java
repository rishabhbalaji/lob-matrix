package com.lobmatrix.engine.state;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OrderBookStateManagerTest {

    @Test
    @DisplayName("Verify OrderBookStateManager in-place updates and mid-price/spread tracking")
    void testStateUpdates() {
        OrderBookStateManager manager = new OrderBookStateManager();

        CanonicalMarketSnapshot tick1 = new CanonicalMarketSnapshot(
                "ZERODHA", 738561L, "RELIANCE",
                1_000_000L, 2_000_000L, 1700000000L,
                2500.0, 10, 500000, 2498.0, 2,
                new double[]{2499.5, 2499.0}, new long[]{100, 200}, new int[]{1, 2},
                new double[]{2500.5, 2501.0}, new long[]{150, 250}, new int[]{2, 3},
                BookStateTag.NORMAL
        );

        MutableOrderBookState state = manager.applySnapshot(tick1);

        assertThat(manager.getActiveInstrumentCount()).isEqualTo(1);
        assertThat(state.getInstrumentToken()).isEqualTo(738561L);
        assertThat(state.getSymbol()).isEqualTo("RELIANCE");
        assertThat(state.getMidPrice()).isCloseTo(2500.0, within(0.001));
        assertThat(state.getSpread()).isCloseTo(1.0, within(0.001));
        assertThat(state.getStateTag()).isEqualTo(BookStateTag.NORMAL);
        assertThat(state.getUpdateCount()).isEqualTo(1L);

        // Apply tick 2 with price move
        CanonicalMarketSnapshot tick2 = new CanonicalMarketSnapshot(
                "ZERODHA", 738561L, "RELIANCE",
                2_000_000L, 3_000_000L, 1700000001L,
                2502.0, 50, 500050, 2498.5, 2,
                new double[]{2501.5, 2501.0}, new long[]{300, 400}, new int[]{4, 5},
                new double[]{2502.5, 2503.0}, new long[]{350, 450}, new int[]{5, 6},
                BookStateTag.NORMAL
        );

        manager.applySnapshot(tick2);

        assertThat(state.getMidPrice()).isCloseTo(2502.0, within(0.001));
        assertThat(state.getUpdateCount()).isEqualTo(2L);
        assertThat(state.getLtp()).isEqualTo(2502.0);
    }

    @Test
    @DisplayName("Verify crossed book state detection and anomaly duration tracking")
    void testCrossedBookDurationTracking() {
        OrderBookStateManager manager = new OrderBookStateManager();

        // Tick 1: Normal
        CanonicalMarketSnapshot normalTick = new CanonicalMarketSnapshot(
                "MOCK", 1001L, "INFY",
                1_000_000_000L, 1_700_000_000L, 1700000000L,
                1500.0, 10, 1000, 1500.0, 1,
                new double[]{1499.0}, new long[]{100}, new int[]{1},
                new double[]{1501.0}, new long[]{100}, new int[]{1},
                BookStateTag.NORMAL
        );
        manager.applySnapshot(normalTick);

        // Tick 2: Crossed Book (Bid 1502 > Ask 1500) at t=1,000,000,000 + 10ms
        long t2Nanos = 1_000_000_000L + 10_000_000L;
        CanonicalMarketSnapshot crossedTick = new CanonicalMarketSnapshot(
                "MOCK", 1001L, "INFY",
                t2Nanos, 1_700_000_010L, 1700000000L,
                1500.0, 10, 1000, 1500.0, 1,
                new double[]{1502.0}, new long[]{100}, new int[]{1},
                new double[]{1500.0}, new long[]{100}, new int[]{1},
                BookStateTag.STATE_CROSSED
        );
        MutableOrderBookState state = manager.applySnapshot(crossedTick);
        assertThat(state.getStateTag()).isEqualTo(BookStateTag.STATE_CROSSED);

        // Tick 3: Still Crossed at t=1,000,000,000 + 25ms (duration = 15ms)
        long t3Nanos = 1_000_000_000L + 25_000_000L;
        CanonicalMarketSnapshot crossedTick2 = new CanonicalMarketSnapshot(
                "MOCK", 1001L, "INFY",
                t3Nanos, 1_700_000_025L, 1700000000L,
                1500.0, 10, 1000, 1500.0, 1,
                new double[]{1503.0}, new long[]{100}, new int[]{1},
                new double[]{1500.0}, new long[]{100}, new int[]{1},
                BookStateTag.STATE_CROSSED
        );
        manager.applySnapshot(crossedTick2);

        assertThat(state.getTotalCrossedDurationNanos()).isEqualTo(15_000_000L); // 15 ms
    }
}
