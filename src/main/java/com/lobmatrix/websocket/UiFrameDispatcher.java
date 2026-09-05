package com.lobmatrix.websocket;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bounded, non-blocking handoff from market-data threads to the UI broadcaster.
 * When saturated, the oldest pending UI frame is discarded so clients see the
 * most recent book state rather than a stale replay backlog.
 */
@Component
public class UiFrameDispatcher implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(UiFrameDispatcher.class);

    static final int DEFAULT_CAPACITY = 64;
    static final long DEFAULT_MINIMUM_DISPATCH_INTERVAL_MS = 100L;

    private final ArrayBlockingQueue<CanonicalMarketSnapshot> frames;
    private final long minimumDispatchIntervalNanos;
    private final Consumer<CanonicalMarketSnapshot> consumer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong publishedFrames = new AtomicLong();
    private final AtomicLong dispatchedFrames = new AtomicLong();
    private final AtomicLong droppedOldestFrames = new AtomicLong();

    private volatile Thread dispatcherThread;

    @Autowired
    public UiFrameDispatcher(OrderBookBroadcastService broadcastService) {
        this(DEFAULT_CAPACITY, DEFAULT_MINIMUM_DISPATCH_INTERVAL_MS, broadcastService::dispatch);
    }

    UiFrameDispatcher(
            int capacity,
            long minimumDispatchIntervalMs,
            Consumer<CanonicalMarketSnapshot> consumer
    ) {
        if (capacity < 1) {
            throw new IllegalArgumentException("UI frame buffer capacity must be at least one.");
        }
        if (minimumDispatchIntervalMs < 0) {
            throw new IllegalArgumentException("UI dispatch interval must not be negative.");
        }

        this.frames = new ArrayBlockingQueue<>(capacity);
        this.minimumDispatchIntervalNanos = TimeUnit.MILLISECONDS.toNanos(minimumDispatchIntervalMs);
        this.consumer = consumer;
    }

    /**
     * Never blocks the caller. Under UI overload, drops the oldest pending frame
     * before publishing the newest state.
     */
    public void publish(CanonicalMarketSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        publishedFrames.incrementAndGet();
        while (!frames.offer(snapshot)) {
            CanonicalMarketSnapshot discarded = frames.poll();
            if (discarded != null) {
                droppedOldestFrames.incrementAndGet();
            }
        }
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        dispatcherThread = Thread.ofPlatform()
                .name("orderbook-ui-dispatcher")
                .daemon(true)
                .start(this::runLoop);
        log.info("UI frame dispatcher started: capacity={}, refresh={}ms, overflow=DROP_OLDEST_UI_FRAME",
                frames.size() + frames.remainingCapacity(),
                TimeUnit.NANOSECONDS.toMillis(minimumDispatchIntervalNanos));
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            Thread thread = dispatcherThread;
            if (thread != null) {
                thread.interrupt();
            }
            frames.clear();
            log.info("UI frame dispatcher stopped: published={}, dispatched={}, droppedOldest={}",
                    publishedFrames.get(), dispatchedFrames.get(), droppedOldestFrames.get());
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    private void runLoop() {
        long nextDispatchAt = 0L;

        while (running.get()) {
            try {
                CanonicalMarketSnapshot newest = frames.poll(250, TimeUnit.MILLISECONDS);
                if (newest == null) {
                    continue;
                }

                CanonicalMarketSnapshot candidate;
                while ((candidate = frames.poll()) != null) {
                    newest = candidate;
                    droppedOldestFrames.incrementAndGet();
                }

                long now = System.nanoTime();
                long delay = nextDispatchAt - now;
                if (delay > 0) {
                    TimeUnit.NANOSECONDS.sleep(delay);
                }

                if (!running.get()) {
                    return;
                }

                consumer.accept(newest);
                dispatchedFrames.incrementAndGet();
                nextDispatchAt = System.nanoTime() + minimumDispatchIntervalNanos;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException exception) {
                log.error("UI frame dispatch failed; retaining market-feed isolation", exception);
            }
        }
    }

    public long publishedFrameCount() {
        return publishedFrames.get();
    }

    public long dispatchedFrameCount() {
        return dispatchedFrames.get();
    }

    public long droppedOldestFrameCount() {
        return droppedOldestFrames.get();
    }

    public int queuedFrameCount() {
        return frames.size();
    }
}
