package com.lobmatrix.websocket;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookSnapshotMessageTest {

    @Test
    void mapsCanonicalSnapshotIntoStableDashboardPayload() {
        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK", 1001L, "SYM_1001", 10L, 1_700_000_000_000_000L, 1_700_000_000L,
                2500.25, 50L, 125_000L, 2500.10, 2,
                new double[]{2500.00, 2499.90}, new long[]{900L, 700L}, new int[]{4, 3},
                new double[]{2500.50, 2500.60}, new long[]{800L, 600L}, new int[]{2, 1},
                BookStateTag.NORMAL
        );

        OrderBookSnapshotMessage message = OrderBookSnapshotMessage.from(snapshot);

        assertThat(message.type()).isEqualTo("orderbook_snapshot");
        assertThat(message.source()).isEqualTo("MOCK");
        assertThat(message.token()).isEqualTo(1001L);
        assertThat(message.symbol()).isEqualTo("SYM_1001");
        assertThat(message.lastPrice()).isEqualTo(2500.25);
        assertThat(message.midPrice()).isEqualTo(2500.25);
        assertThat(message.spread()).isEqualTo(0.50);
        assertThat(message.bookState()).isEqualTo("NORMAL");
        assertThat(message.bids()).containsExactly(
                new OrderBookLevel(2500.00, 900L, 4),
                new OrderBookLevel(2499.90, 700L, 3)
        );
        assertThat(message.asks()).containsExactly(
                new OrderBookLevel(2500.50, 800L, 2),
                new OrderBookLevel(2500.60, 600L, 1)
        );
    }
}
