package com.lobmatrix.engine.resample;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Zero-Lookahead Fixed-Time Resampling Engine (LOCF Operator).
 * Transforms irregular continuous event-time ticks into a regular discrete clock grid.
 */
public class FixedTimeResamplingEngine {

    private static final Logger log = LoggerFactory.getLogger(FixedTimeResamplingEngine.class);

    private final long deltaNanos;
    private long nextGridNanos = -1L;
    private long gridSequence = 1L;
    private CanonicalMarketSnapshot lastObservation = null;

    /**
     * @param deltaIntervalMs Grid step resolution Delta t in milliseconds (e.g. 100ms, 250ms, 500ms, 1000ms)
     */
    public FixedTimeResamplingEngine(long deltaIntervalMs) {
        if (deltaIntervalMs <= 0) {
            throw new IllegalArgumentException("deltaIntervalMs must be positive, got: " + deltaIntervalMs);
        }
        this.deltaNanos = deltaIntervalMs * 1_000_000L;
    }

    /**
     * Ingests an incoming continuous tick and advances the discrete grid, emitting all
     * resampled grid points up to the arrival time without lookahead.
     *
     * @param incoming Latest CanonicalMarketSnapshot
     * @return List of newly triggered ResampledGridPoints
     */
    public List<ResampledGridPoint> onTick(CanonicalMarketSnapshot incoming) {
        List<ResampledGridPoint> emitted = new ArrayList<>();
        if (incoming == null) return emitted;

        long tickNanos = incoming.clientArrivalNanos();

        // Initialize grid anchor on first tick: first evaluated grid boundary is t0 + deltaNanos
        if (nextGridNanos < 0) {
            nextGridNanos = tickNanos + deltaNanos;
            lastObservation = incoming;
            return emitted;
        }

        // Advance all discrete clock grid points strictly at or before this tick arrival
        while (nextGridNanos <= tickNanos && lastObservation != null) {
            long ageNanos = nextGridNanos - lastObservation.clientArrivalNanos();
            emitted.add(new ResampledGridPoint(
                    gridSequence++,
                    nextGridNanos,
                    deltaNanos,
                    lastObservation,
                    Math.max(0L, ageNanos)
            ));
            nextGridNanos += deltaNanos;
        }

        // Update the last known observation AFTER advancing previous grid points (enforcing causal LOCF)
        this.lastObservation = incoming;

        return emitted;
    }

    /**
     * Manually advances the clock grid to a specific point in time (useful for simulating clock/timer ticks).
     */
    public List<ResampledGridPoint> advanceTo(long currentNanos) {
        List<ResampledGridPoint> emitted = new ArrayList<>();
        if (lastObservation == null || nextGridNanos < 0) return emitted;

        while (nextGridNanos <= currentNanos) {
            long ageNanos = nextGridNanos - lastObservation.clientArrivalNanos();
            emitted.add(new ResampledGridPoint(
                    gridSequence++,
                    nextGridNanos,
                    deltaNanos,
                    lastObservation,
                    Math.max(0L, ageNanos)
            ));
            nextGridNanos += deltaNanos;
        }
        return emitted;
    }

    public long getDeltaNanos() { return deltaNanos; }
    public long getGridSequence() { return gridSequence; }
    public void reset() {
        this.nextGridNanos = -1L;
        this.gridSequence = 1L;
        this.lastObservation = null;
    }
}
