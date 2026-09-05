package com.lobmatrix.websocket;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Stable dashboard WebSocket message.
 *
 * <p>M5P3S2 extends the message with display-ready percentages for the live
 * depth-imbalance speedometer and Lee-Ready trade-strength gauge. Existing
 * fields remain unchanged so current dashboard consumers remain compatible.</p>
 */
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
        double depthImbalancePercent,
        double tradeStrengthPercent,
        String predictionMode,
        double probabilityDownPercent,
        double probabilityNeutralPercent,
        double probabilityUpPercent,
        double predictionScorePercent,
        boolean calibratedProbabilities,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks
) {
    public static OrderBookSnapshotMessage from(CanonicalMarketSnapshot snapshot) {
        return from(snapshot, LiveDashboardFeatureService.LiveDashboardFeatures.neutral());
    }

    public static OrderBookSnapshotMessage from(
            CanonicalMarketSnapshot snapshot,
            LiveDashboardFeatureService.LiveDashboardFeatures features
    ) {
        return from(
                snapshot,
                features,
                LiveDashboardPredictionService.LiveDashboardForecast.neutral(
                        com.lobmatrix.inference.InferenceMode.MODE_BASELINE_ACTIVE
                )
        );
    }

    public static OrderBookSnapshotMessage from(
            CanonicalMarketSnapshot snapshot,
            LiveDashboardFeatureService.LiveDashboardFeatures features,
            LiveDashboardPredictionService.LiveDashboardForecast forecast
    ) {
        double[] bidPrices = snapshot.bidPrices();
        long[] bidQuantities = snapshot.bidQuantities();
        int[] bidOrders = snapshot.bidOrders();
        double[] askPrices = snapshot.askPrices();
        long[] askQuantities = snapshot.askQuantities();
        int[] askOrders = snapshot.askOrders();

        LiveDashboardFeatureService.LiveDashboardFeatures safeFeatures =
                features != null ? features : LiveDashboardFeatureService.LiveDashboardFeatures.neutral();
        LiveDashboardPredictionService.LiveDashboardForecast safeForecast =
                forecast != null
                        ? forecast
                        : LiveDashboardPredictionService.LiveDashboardForecast.neutral(
                                com.lobmatrix.inference.InferenceMode.MODE_BASELINE_ACTIVE
                        );

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
                safeFeatures.depthImbalancePercent(),
                safeFeatures.tradeStrengthPercent(),
                safeForecast.predictionMode(),
                safeForecast.probabilityDownPercent(),
                safeForecast.probabilityNeutralPercent(),
                safeForecast.probabilityUpPercent(),
                safeForecast.predictionScorePercent(),
                safeForecast.calibratedProbabilities(),
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
