package com.lobmatrix.engine.resample;

import com.lobmatrix.core.model.CanonicalMarketSnapshot;

import java.util.Objects;

/**
 * Represents an aligned, discrete clock-grid observation sampled strictly at or before T_k.
 */
public record ResampledGridPoint(
        long gridSequence,                  // Discrete step index k (0, 1, 2...)
        long gridNanos,                     // Monotonic timestamp of the grid point T_k
        long deltaIntervalNanos,            // Grid resolution Delta t in nanoseconds
        CanonicalMarketSnapshot snapshot,   // Carried-forward snapshot (strictly t_i <= T_k)
        long snapshotAgeNanos               // Staleness / Age: T_k - t_snapshot
) {
    public ResampledGridPoint {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
    }

    public double snapshotAgeMs() {
        return snapshotAgeNanos / 1_000_000.0;
    }
}
