package com.lobmatrix.websocket;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import com.lobmatrix.engine.math.MicropriceCalculator;
import com.lobmatrix.engine.math.MultiLevelOFICalculator;
import com.lobmatrix.engine.math.OrderBookImbalanceCalculator;
import com.lobmatrix.engine.math.TradeStrengthClassifier;
import com.lobmatrix.inference.AdaptivePredictionService;
import com.lobmatrix.inference.InferenceMode;
import com.lobmatrix.inference.PredictionResult;
import com.lobmatrix.parquet.ParquetFeatureRecord;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M5P3S3 bounded bridge from UI-dispatched canonical snapshots to the
 * existing AdaptivePredictionService.
 *
 * <p>The service is called only after UiFrameDispatcher has selected the
 * latest browser frame. It never runs on the raw market-feed thread. Per
 * instrument it retains only the immediately preceding snapshot and one
 * existing TradeStrengthClassifier: there is no unbounded tick history.</p>
 *
 * <p>When verified ONNX artifacts are available, returned probabilities are
 * calibrated model values. In cold-start baseline mode, the service produces
 * clearly labelled, score-derived display probabilities only; they are not
 * calibrated forecasts.</p>
 */
@Service
public class LiveDashboardPredictionService implements AutoCloseable {

    private static final double[] TOP5_EXP_WEIGHTS =
            OrderBookImbalanceCalculator.generateExponentialWeights(5, 0.5);
    private static final double[] TOP5_UNIFORM_OFI_WEIGHTS =
            {0.2, 0.2, 0.2, 0.2, 0.2};
    private static final double[] TOP5_EXP_OFI_WEIGHTS =
            {1.0, 0.6065, 0.3678, 0.2231, 0.1353};

    private final AdaptivePredictionService predictionService;
    private final ConcurrentMap<Long, InstrumentState> stateByToken =
            new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public LiveDashboardPredictionService() {
        this(AdaptivePredictionService.initialize());
    }

    LiveDashboardPredictionService(AdaptivePredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /**
     * Produces a bounded, display-safe forecast for the supplied snapshot.
     *
     * @param snapshot latest UI-dispatched market state
     * @return one live dashboard forecast
     */
    public LiveDashboardForecast calculate(CanonicalMarketSnapshot snapshot) {
        if (snapshot == null) {
            return LiveDashboardForecast.neutral(InferenceMode.MODE_BASELINE_ACTIVE);
        }

        InstrumentState state = stateByToken.computeIfAbsent(
                snapshot.instrumentToken(),
                ignored -> new InstrumentState()
        );

        synchronized (state) {
            state.tradeStrengthClassifier.recordTrade(snapshot, snapshot.ltq());

            ParquetFeatureRecord record = featureRecord(
                    snapshot,
                    state.previousSnapshot,
                    state.tradeStrengthClassifier,
                    sequence.incrementAndGet()
            );

            PredictionResult result = predictionService.evaluate(record);
            state.previousSnapshot = snapshot;

            return fromPrediction(result);
        }
    }

    int trackedInstrumentCount() {
        return stateByToken.size();
    }

    @Override
    public void close() {
        predictionService.close();
    }

    private static ParquetFeatureRecord featureRecord(
            CanonicalMarketSnapshot snapshot,
            CanonicalMarketSnapshot previousSnapshot,
            TradeStrengthClassifier tradeStrengthClassifier,
            long sequence
    ) {
        double midPrice = finiteOrZero(snapshot.midPrice());
        double spread = finiteOrZero(snapshot.spread());
        double relativeSpreadBps = midPrice > 0.0 ? spread / midPrice * 10_000.0 : 0.0;

        double level1Obi = finiteOrZero(
                OrderBookImbalanceCalculator.calculateLevel1OBI(snapshot));
        double totalObi = finiteOrZero(
                OrderBookImbalanceCalculator.calculateTotalOBI(snapshot));
        double weightedObiLinear = finiteOrZero(
                OrderBookImbalanceCalculator.calculateWeightedOBI(
                        snapshot,
                        OrderBookImbalanceCalculator.TOP5_LINEAR_WEIGHTS
                ));
        double weightedObiExp = finiteOrZero(
                OrderBookImbalanceCalculator.calculateWeightedOBI(snapshot, TOP5_EXP_WEIGHTS));
        double microprice = finiteOrZero(
                MicropriceCalculator.calculateLevel1Microprice(snapshot));
        double micropricePressure = finiteOrZero(
                MicropriceCalculator.calculateMicropricePressure(snapshot));
        double micropricePressureBps = finiteOrZero(
                MicropriceCalculator.calculateMicropricePressureBps(snapshot));
        double multiLevelMicroprice = finiteOrZero(
                MicropriceCalculator.calculateMultiLevelMicroprice(
                        snapshot,
                        OrderBookImbalanceCalculator.TOP5_LINEAR_WEIGHTS
                ));

        double level1Ofi = previousSnapshot == null ? 0.0 : finiteOrZero(
                MultiLevelOFICalculator.calculateLevel1OFI(previousSnapshot, snapshot));
        double multiLevelOfiUniform = previousSnapshot == null ? 0.0 : finiteOrZero(
                MultiLevelOFICalculator.calculateMultiLevelOFI(
                        previousSnapshot,
                        snapshot,
                        TOP5_UNIFORM_OFI_WEIGHTS
                ));
        double multiLevelOfiExp = previousSnapshot == null ? 0.0 : finiteOrZero(
                MultiLevelOFICalculator.calculateMultiLevelOFI(
                        previousSnapshot,
                        snapshot,
                        TOP5_EXP_OFI_WEIGHTS
                ));

        double tradeStrength = finiteOrZero(tradeStrengthClassifier.calculateTradeStrength());
        double buyPressure = finiteOrZero(tradeStrengthClassifier.calculateBuyPressure());
        double sellPressure = finiteOrZero(tradeStrengthClassifier.calculateSellPressure());
        double snapshotAgeMs = 0.0;

        return new ParquetFeatureRecord(
                sequence,
                snapshot.clientArrivalNanos(),
                0L,
                snapshot.instrumentToken(),
                snapshot.symbol(),
                snapshotAgeMs,
                bestPrice(snapshot.bidPrices()),
                bestPrice(snapshot.askPrices()),
                midPrice,
                spread,
                relativeSpreadBps,
                finiteOrZero(snapshot.ltp()),
                snapshot.cumulativeVolume(),
                finiteOrZero(snapshot.dayVwap()),
                level1Obi,
                totalObi,
                weightedObiLinear,
                weightedObiExp,
                microprice,
                micropricePressure,
                micropricePressureBps,
                multiLevelMicroprice,
                level1Ofi,
                multiLevelOfiUniform,
                multiLevelOfiExp,
                tradeStrength,
                buyPressure,
                sellPressure,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                0,
                0,
                0,
                0,
                0,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN
        );
    }

    private static LiveDashboardForecast fromPrediction(PredictionResult result) {
        InferenceMode mode = result.mode();
        if (mode == InferenceMode.MODE_AI_PREDICTIVE_ACTIVE) {
            return new LiveDashboardForecast(
                    mode.name(),
                    probabilityPercent(result.probabilityDown()),
                    probabilityPercent(result.probabilityNeutral()),
                    probabilityPercent(result.probabilityUp()),
                    clampPercent(result.bullishScore() * 100.0),
                    true
            );
        }

        return baselineForecast(result.bullishScore(), mode);
    }

    /**
     * The baseline formula has a directional score but no calibrated class
     * probabilities. Convert it to symmetric display weights and explicitly
     * mark calibratedProbabilities false in the outbound message.
     */
    private static LiveDashboardForecast baselineForecast(
            double bullishScore,
            InferenceMode mode
    ) {
        double score = Math.max(-1.0, Math.min(1.0, finiteOrZero(bullishScore)));
        double directionalMass = Math.abs(score) * 100.0;
        double neutral = 100.0 - directionalMass;
        double up = score > 0.0 ? directionalMass : 0.0;
        double down = score < 0.0 ? directionalMass : 0.0;

        return new LiveDashboardForecast(
                mode.name(),
                down,
                neutral,
                up,
                clampPercent(score * 100.0),
                false
        );
    }

    private static double probabilityPercent(Float value) {
        if (value == null || !Float.isFinite(value)) {
            return 0.0;
        }
        return clampPercent(value * 100.0);
    }

    private static double clampPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double bestPrice(double[] prices) {
        return prices.length > 0 && Double.isFinite(prices[0]) ? prices[0] : 0.0;
    }

    private static final class InstrumentState {
        private final TradeStrengthClassifier tradeStrengthClassifier =
                new TradeStrengthClassifier();
        private CanonicalMarketSnapshot previousSnapshot;
    }

    /**
     * Immutable display contract for M5P3S3 forecast cards.
     */
    public record LiveDashboardForecast(
            String predictionMode,
            double probabilityDownPercent,
            double probabilityNeutralPercent,
            double probabilityUpPercent,
            double predictionScorePercent,
            boolean calibratedProbabilities
    ) {
        static LiveDashboardForecast neutral(InferenceMode mode) {
            return new LiveDashboardForecast(
                    mode.name(),
                    0.0,
                    100.0,
                    0.0,
                    0.0,
                    false
            );
        }
    }
}
