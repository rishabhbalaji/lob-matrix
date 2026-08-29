package com.lobmatrix.engine.resample;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks snapshot freshness, empirical arrival gaps (Delta t_i), and enforces staleness thresholds.
 */
public class SnapshotAgeTracker {

    private static final Logger log = LoggerFactory.getLogger(SnapshotAgeTracker.class);
    public static final long DEFAULT_MAX_STALENESS_MS = 5_000L; // 5 seconds

    private final long maxStalenessNanos;
    private long prevArrivalNanos = -1L;
    private long totalGapsNanos = 0L;
    private long gapCount = 0L;
    private long minGapNanos = Long.MAX_VALUE;
    private long maxGapNanos = 0L;
    private long staleDropCount = 0L;

    public SnapshotAgeTracker(long maxStalenessMs) {
        this.maxStalenessNanos = (maxStalenessMs > 0 ? maxStalenessMs : DEFAULT_MAX_STALENESS_MS) * 1_000_000L;
    }

    public SnapshotAgeTracker() {
        this(DEFAULT_MAX_STALENESS_MS);
    }

    /**
     * Records an incoming continuous tick and tracks inter-arrival gaps.
     */
    public void recordTickArrival(CanonicalMarketSnapshot snapshot) {
        if (snapshot == null) return;

        long arrivalNanos = snapshot.clientArrivalNanos();
        if (prevArrivalNanos > 0) {
            long gap = arrivalNanos - prevArrivalNanos;
            if (gap >= 0) {
                totalGapsNanos += gap;
                gapCount++;
                if (gap < minGapNanos) minGapNanos = gap;
                if (gap > maxGapNanos) maxGapNanos = gap;
            }
        }
        this.prevArrivalNanos = arrivalNanos;
    }

    /**
     * Calculates the age of a snapshot relative to a discrete clock grid point.
     *
     * @param gridPoint Resampled grid observation
     * @return Age in milliseconds
     */
    public double calculateAgeMs(ResampledGridPoint gridPoint) {
        if (gridPoint == null) return Double.NaN;
        return gridPoint.snapshotAgeMs();
    }

    /**
     * Returns true if the resampled grid point is older than the staleness threshold.
     */
    public boolean isStale(ResampledGridPoint gridPoint) {
        if (gridPoint == null) return true;
        boolean stale = gridPoint.snapshotAgeNanos() > maxStalenessNanos;
        if (stale) {
            staleDropCount++;
        }
        return stale;
    }

    /**
     * Empirical average packet arrival gap in milliseconds.
     */
    public double getAverageArrivalGapMs() {
        if (gapCount == 0) return 0.0;
        return (totalGapsNanos / (double) gapCount) / 1_000_000.0;
    }

    public double getMinArrivalGapMs() {
        if (gapCount == 0) return 0.0;
        return minGapNanos / 1_000_000.0;
    }

    public double getMaxArrivalGapMs() {
        if (gapCount == 0) return 0.0;
        return maxGapNanos / 1_000_000.0;
    }

    public long getGapCount() { return gapCount; }
    public long getStaleDropCount() { return staleDropCount; }
    public long getMaxStalenessMs() { return maxStalenessNanos / 1_000_000L; }

    public void reset() {
        this.prevArrivalNanos = -1L;
        this.totalGapsNanos = 0L;
        this.gapCount = 0L;
        this.minGapNanos = Long.MAX_VALUE;
        this.maxGapNanos = 0L;
        this.staleDropCount = 0L;
    }
}
