package com.lobmatrix.websocket;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5P3S1: The live dashboard depth ladder renders 20 bid rows and 20 ask
 * rows. This test proves the WebSocket serialization layer preserves all
 * 20 levels per side rather than silently truncating to a smaller depth.
 */
class OrderBookSnapshotMessageLevel20Test {

    @Test
    void preservesAllTwentyLevelsPerSideWhenSerializingCanonicalSnapshot() {
        int depth = 20;
        double[] bidPrices = new double[depth];
        long[] bidQuantities = new long[depth];
        int[] bidOrders = new int[depth];
        double[] askPrices = new double[depth];
        long[] askQuantities = new long[depth];
        int[] askOrders = new int[depth];

        for (int i = 0; i < depth; i++) {
            bidPrices[i] = 2500.00 - (i * 0.10);
            bidQuantities[i] = 100L + i;
            bidOrders[i] = 1 + i;
            askPrices[i] = 2500.50 + (i * 0.10);
            askQuantities[i] = 200L + i;
            askOrders[i] = 2 + i;
        }

        CanonicalMarketSnapshot snapshot = new CanonicalMarketSnapshot(
                "MOCK",
                1001L,
                "SYM1001",
                10L,
                1_700_000_000_000_000L,
                1_700_000_000L,
                2500.25,
                50L,
                125_000L,
                2500.10,
                depth,
                bidPrices,
                bidQuantities,
                bidOrders,
                askPrices,
                askQuantities,
                askOrders,
                BookStateTag.NORMAL
        );

        OrderBookSnapshotMessage message = OrderBookSnapshotMessage.from(snapshot);

        assertThat(message.bids()).hasSize(depth);
        assertThat(message.asks()).hasSize(depth);
        assertThat(message.bids().get(0).price()).isEqualTo(2500.00);
        assertThat(message.bids().get(depth - 1).price()).isEqualTo(bidPrices[depth - 1]);
        assertThat(message.asks().get(0).price()).isEqualTo(2500.50);
        assertThat(message.asks().get(depth - 1).price()).isEqualTo(askPrices[depth - 1]);
    }
}
