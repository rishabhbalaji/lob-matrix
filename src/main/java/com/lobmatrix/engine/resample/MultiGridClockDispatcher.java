package com.lobmatrix.engine.resample;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Synchronized Multi-Grid Clock Dispatcher.
 * Manages parallel resampling engines across the 5 standard frequencies:
 * 100ms, 250ms, 500ms, 1000ms, and 2000ms.
 */
public class MultiGridClockDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MultiGridClockDispatcher.class);

    // The 5 standard experimental sampling resolutions for the 2D Information Surface
    public static final long[] STANDARD_INTERVALS_MS = {100L, 250L, 500L, 1000L, 2000L};

    // Map: instrumentToken -> Map<IntervalMs, FixedTimeResamplingEngine>
    private final Map<Long, Map<Long, FixedTimeResamplingEngine>> tokenEngines = new ConcurrentHashMap<>();
    private final long[] intervalsMs;

    // Listeners: (intervalMs, ResampledGridPoint) -> void
    private final List<BiConsumer<Long, ResampledGridPoint>> listeners = new CopyOnWriteArrayList<>();

    public MultiGridClockDispatcher(long[] intervalsMs) {
        this.intervalsMs = intervalsMs != null && intervalsMs.length > 0 ? intervalsMs.clone() : STANDARD_INTERVALS_MS;
    }

    public MultiGridClockDispatcher() {
        this(STANDARD_INTERVALS_MS);
    }

    /**
     * Registers a listener receiving resampled points for any grid frequency.
     */
    public void registerListener(BiConsumer<Long, ResampledGridPoint> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void unregisterListener(BiConsumer<Long, ResampledGridPoint> listener) {
        listeners.remove(listener);
    }

    /**
     * Ingests a continuous raw market snapshot and dispatches to all configured grid engines.
     *
     * @param snapshot Latest CanonicalMarketSnapshot
     * @return Map of IntervalMs -> List of newly emitted ResampledGridPoints
     */
    public Map<Long, List<ResampledGridPoint>> onTick(CanonicalMarketSnapshot snapshot) {
        Map<Long, List<ResampledGridPoint>> result = new HashMap<>();
        if (snapshot == null) return result;

        long token = snapshot.instrumentToken();
        Map<Long, FixedTimeResamplingEngine> engines = tokenEngines.computeIfAbsent(token, k -> {
            Map<Long, FixedTimeResamplingEngine> map = new HashMap<>();
            for (long interval : intervalsMs) {
                map.put(interval, new FixedTimeResamplingEngine(interval));
            }
            return map;
        });

        for (long interval : intervalsMs) {
            FixedTimeResamplingEngine engine = engines.get(interval);
            if (engine != null) {
                List<ResampledGridPoint> emitted = engine.onTick(snapshot);
                if (!emitted.isEmpty()) {
                    result.put(interval, emitted);
                    for (ResampledGridPoint pt : emitted) {
                        dispatchToListeners(interval, pt);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Manually advances all grid clocks for all tokens to a specific time.
     */
    public Map<Long, List<ResampledGridPoint>> advanceTo(long currentNanos) {
        Map<Long, List<ResampledGridPoint>> result = new HashMap<>();

        for (Map<Long, FixedTimeResamplingEngine> engines : tokenEngines.values()) {
            for (long interval : intervalsMs) {
                FixedTimeResamplingEngine engine = engines.get(interval);
                if (engine != null) {
                    List<ResampledGridPoint> emitted = engine.advanceTo(currentNanos);
                    if (!emitted.isEmpty()) {
                        result.computeIfAbsent(interval, k -> new ArrayList<>()).addAll(emitted);
                        for (ResampledGridPoint pt : emitted) {
                            dispatchToListeners(interval, pt);
                        }
                    }
                }
            }
        }

        return result;
    }

    private void dispatchToListeners(long interval, ResampledGridPoint point) {
        for (BiConsumer<Long, ResampledGridPoint> listener : listeners) {
            try {
                listener.accept(interval, point);
            } catch (Exception e) {
                log.error("Error in multi-grid listener dispatch for interval {}ms", interval, e);
            }
        }
    }

    public long[] getIntervalsMs() { return intervalsMs.clone(); }
    public int getTrackedTokenCount() { return tokenEngines.size(); }

    public void reset() {
        tokenEngines.clear();
        log.info("MultiGridClockDispatcher reset cleanly.");
    }
}
