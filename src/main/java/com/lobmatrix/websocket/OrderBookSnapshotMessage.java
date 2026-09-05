package com.lobmatrix.websocket;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;

import java.util.List;
import java.util.stream.IntStream;

public record OrderBookSnapshotMessage(
        String type,
        String source,
        long token,
        String symbol,
        long timestampMicros,
        double lastPrice,
        long lastTradedQuantity,
        long volume,
        double dayVwap,
        double midPrice,
        double spread,
        String bookState,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks
) {
    public static OrderBookSnapshotMessage from(CanonicalMarketSnapshot snapshot) {
        double[] bidPrices = snapshot.bidPrices();
        long[] bidQuantities = snapshot.bidQuantities();
        int[] bidOrders = snapshot.bidOrders();
        double[] askPrices = snapshot.askPrices();
        long[] askQuantities = snapshot.askQuantities();
        int[] askOrders = snapshot.askOrders();

        return new OrderBookSnapshotMessage(
                "orderbook_snapshot",
                snapshot.sourceId(),
                snapshot.instrumentToken(),
                snapshot.symbol(),
                snapshot.clientArrivalMicros(),
                snapshot.ltp(),
                snapshot.ltq(),
                snapshot.cumulativeVolume(),
                snapshot.dayVwap(),
                snapshot.midPrice(),
                snapshot.spread(),
                snapshot.stateTag().name(),
                levels(bidPrices, bidQuantities, bidOrders),
                levels(askPrices, askQuantities, askOrders)
        );
    }

    private static List<OrderBookLevel> levels(double[] prices, long[] quantities, int[] orders) {
        int size = Math.min(prices.length, Math.min(quantities.length, orders.length));
        return IntStream.range(0, size)
                .mapToObj(index -> new OrderBookLevel(prices[index], quantities[index], orders[index]))
                .toList();
    }
}
