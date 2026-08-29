package com.lobmatrix.core.adapter;

import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-speed deterministic synthetic market data generator implementing MarketFeedAdapter SPI.
 * Emits realistic Top-5 or Top-20 depth ticks for offline development and load testing.
 */
public class MockMarketReplayFeeder implements MarketFeedAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockMarketReplayFeeder.class);

    private final String sourceId;
    private final int depthLevels;
    private final long emissionIntervalMs;
    private final Random random;
    private final Set<Long> subscribedTokens = new CopyOnWriteArraySet<>();
    private final List<Consumer<CanonicalMarketSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;
    private volatile FeedStatus status = FeedStatus.DISCONNECTED;

    // Track simulated state per token
    private final Map<Long, Double> tokenPrices = new ConcurrentHashMap<>();
    private final Map<Long, Long> tokenVolumes = new ConcurrentHashMap<>();

    public MockMarketReplayFeeder(String sourceId, int depthLevels, long emissionIntervalMs, long seed) {
        this.sourceId = sourceId != null ? sourceId : "MOCK";
        this.depthLevels = depthLevels > 0 ? depthLevels : 5;
        this.emissionIntervalMs = emissionIntervalMs > 0 ? emissionIntervalMs : 10;
        this.random = new Random(seed);
    }

    public MockMarketReplayFeeder() {
        this("MOCK", 5, 10, 42L); // Default: 5 levels, 10ms interval (100 ticks/sec), seed 42
    }

    @Override
    public String getSourceId() {
        return sourceId;
    }

    @Override
    public FeedStatus getStatus() {
        return status;
    }

    @Override
    public synchronized void connect() {
        if (isRunning.compareAndSet(false, true)) {
            status = FeedStatus.CONNECTING;
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mock-feeder-thread");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::emitTicks, 0, emissionIntervalMs, TimeUnit.MILLISECONDS);
            status = FeedStatus.CONNECTED;
            log.info("MockMarketReplayFeeder started. Emitting every {} ms (depth={} levels).", emissionIntervalMs, depthLevels);
        }
    }

    @Override
    public synchronized void disconnect() {
        if (isRunning.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            status = FeedStatus.DISCONNECTED;
            log.info("MockMarketReplayFeeder stopped.");
        }
    }

    @Override
    public void subscribe(Set<Long> tokens) {
        if (tokens != null) {
            for (Long token : tokens) {
                subscribedTokens.add(token);
                tokenPrices.putIfAbsent(token, 1000.0 + (random.nextDouble() * 1500.0));
                tokenVolumes.putIfAbsent(token, 100_000L);
            }
        }
    }

    @Override
    public void unsubscribe(Set<Long> tokens) {
        if (tokens != null) {
            subscribedTokens.removeAll(tokens);
        }
    }

    @Override
    public void registerListener(Consumer<CanonicalMarketSnapshot> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void unregisterListener(Consumer<CanonicalMarketSnapshot> listener) {
        listeners.remove(listener);
    }

    /**
     * Generates a single deterministic tick for a specific token (useful for step-by-step unit testing).
     */
    public CanonicalMarketSnapshot generateNextSnapshot(long token, String symbol) {
        double currentPrice = tokenPrices.compute(token, (k, v) -> {
            double old = (v != null) ? v : 2500.0;
            // Random walk: +/- 0.05 to 0.50
            double delta = (random.nextDouble() - 0.49) * 0.50;
            return Math.round((old + delta) * 20.0) / 20.0; // Snap to 0.05 tick size
        });

        long currentVol = tokenVolumes.compute(token, (k, v) -> (v != null ? v : 1000L) + random.nextInt(50));
        long ltq = 10 + random.nextInt(100);

        long nowNanos = System.nanoTime();
        long nowMicros = Instant.now().toEpochMilli() * 1000L;
        long epochSecs = nowMicros / 1_000_000L;

        double halfSpread = 0.25;
        double bestBid = currentPrice - halfSpread;
        double bestAsk = currentPrice + halfSpread;

        double[] bidPrices = new double[depthLevels];
        long[] bidQuantities = new long[depthLevels];
        int[] bidOrders = new int[depthLevels];

        for (int i = 0; i < depthLevels; i++) {
            bidPrices[i] = Math.round((bestBid - (i * 0.10)) * 100.0) / 100.0;
            bidQuantities[i] = 100L + (long) (random.nextInt(500) * (depthLevels - i));
            bidOrders[i] = 1 + random.nextInt(10);
        }

        double[] askPrices = new double[depthLevels];
        long[] askQuantities = new long[depthLevels];
        int[] askOrders = new int[depthLevels];

        for (int i = 0; i < depthLevels; i++) {
            askPrices[i] = Math.round((bestAsk + (i * 0.10)) * 100.0) / 100.0;
            askQuantities[i] = 100L + (long) (random.nextInt(500) * (depthLevels - i));
            askOrders[i] = 1 + random.nextInt(10);
        }

        return new CanonicalMarketSnapshot(
                sourceId,
                token,
                symbol,
                nowNanos,
                nowMicros,
                epochSecs,
                currentPrice,
                ltq,
                currentVol,
                currentPrice - 0.10,
                depthLevels,
                bidPrices,
                bidQuantities,
                bidOrders,
                askPrices,
                askQuantities,
                askOrders,
                BookStateTag.NORMAL
        );
    }

    private void emitTicks() {
        if (!isRunning.get() || listeners.isEmpty() || subscribedTokens.isEmpty()) {
            return;
        }

        for (Long token : subscribedTokens) {
            CanonicalMarketSnapshot snapshot = generateNextSnapshot(token, "SYM_" + token);
            for (Consumer<CanonicalMarketSnapshot> listener : listeners) {
                try {
                    listener.accept(snapshot);
                } catch (Exception e) {
                    log.error("Error in mock feeder listener dispatch", e);
                }
            }
        }
    }
}
