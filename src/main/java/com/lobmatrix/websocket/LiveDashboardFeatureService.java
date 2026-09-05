package com.lobmatrix.websocket;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.math.OrderBookImbalanceCalculator;
import com.lobmatrix.engine.math.TradeStrengthClassifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * M5P3S2: Bounded live feature projection for the browser dashboard.
 *
 * <p>Each active instrument token retains one {@link TradeStrengthClassifier}.
 * The classifier accumulates buy/sell trade attribution using the existing
 * Lee-Ready implementation. Depth imbalance is stateless and is calculated
 * from the current canonical snapshot.</p>
 *
 * <p>This service is deliberately invoked only by the WebSocket broadcaster
 * once a UI frame has passed through {@link UiFrameDispatcher}. It therefore
 * cannot run on, block, or slow the raw market-feed path.</p>
 */
@Service
public class LiveDashboardFeatureService {

    private static final double PERCENT_SCALE = 100.0;
    private static final double MIN_PERCENT = -100.0;
    private static final double MAX_PERCENT = 100.0;

    private final ConcurrentMap<Long, TradeStrengthClassifier> tradeStrengthByToken =
            new ConcurrentHashMap<>();

    /**
     * Calculates the current display metrics and records the snapshot trade
     * volume for the token's bounded live trade-strength state.
     *
     * @param snapshot latest canonical order-book snapshot
     * @return display-safe percentages in the inclusive range [-100, 100]
     */
    public LiveDashboardFeatures calculate(CanonicalMarketSnapshot snapshot) {
        if (snapshot == null) {
            return LiveDashboardFeatures.neutral();
        }

        double depthImbalance = OrderBookImbalanceCalculator.calculateTotalOBI(snapshot);
        double depthImbalancePercent = toPercent(depthImbalance);

        TradeStrengthClassifier classifier = tradeStrengthByToken.computeIfAbsent(
                snapshot.instrumentToken(),
                ignored -> new TradeStrengthClassifier()
        );
        classifier.recordTrade(snapshot, snapshot.ltq());

        double tradeStrengthPercent = toPercent(classifier.calculateTradeStrength());
        return new LiveDashboardFeatures(depthImbalancePercent, tradeStrengthPercent);
    }

    int trackedInstrumentCount() {
        return tradeStrengthByToken.size();
    }

    private static double toPercent(double normalizedValue) {
        if (!Double.isFinite(normalizedValue)) {
            return 0.0;
        }
        return clamp(normalizedValue * PERCENT_SCALE);
    }

    private static double clamp(double value) {
        return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, value));
    }

    /**
     * Immutable browser-display feature values.
     */
    public record LiveDashboardFeatures(
            double depthImbalancePercent,
            double tradeStrengthPercent
    ) {
        static LiveDashboardFeatures neutral() {
            return new LiveDashboardFeatures(0.0, 0.0);
        }
    }
}
