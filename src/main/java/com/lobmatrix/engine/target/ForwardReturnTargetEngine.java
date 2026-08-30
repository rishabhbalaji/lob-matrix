package com.lobmatrix.engine.target;

import com.lobmatrix.engine.resample.ResampledGridPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Computes zero-lookahead forward mid-price log return targets across standard horizons:
 * tau in {1s, 5s, 10s, 30s, 60s}.
 *
 * Maintains a causal sliding time-series window and matches forward marks T_k + tau using LOCF.
 */
public class ForwardReturnTargetEngine {

    private static final Logger log = LoggerFactory.getLogger(ForwardReturnTargetEngine.class);
    public static final long MAX_BUFFER_RETENTION_NANOS = 120_000_000_000L; // 120 seconds

    // Ring/sliding list of chronological resampled points: sorted by gridNanos
    private final List<ResampledGridPoint> history = new ArrayList<>();

    /**
     * Appends a newly generated resampled point T_k to the buffer and prunes stale history.
     */
    public synchronized void appendGridPoint(ResampledGridPoint point) {
        if (point == null) return;
        history.add(point);

        // Prune history older than 120 seconds
        long cutoff = point.gridNanos() - MAX_BUFFER_RETENTION_NANOS;
        while (!history.isEmpty() && history.get(0).gridNanos() < cutoff) {
            history.remove(0);
        }
    }

    /**
     * Computes forward targets for an earlier observation at index in history.
     * Returns null if forward horizon tau=60s is not yet fully available in history.
     */
    public synchronized ForwardReturnTargets computeTargetsIfMatured(int historyIndex) {
        if (historyIndex < 0 || historyIndex >= history.size()) {
            return null;
        }

        ResampledGridPoint basePoint = history.get(historyIndex);
        long baseNanos = basePoint.gridNanos();
        double baseMid = basePoint.snapshot().midPrice();

        if (baseMid <= 0.0) {
            return null;
        }

        long latestAvailableNanos = history.get(history.size() - 1).gridNanos();
        // Check if the maximum horizon (60s) has elapsed
        if (latestAvailableNanos < (baseNanos + ForwardReturnTargets.TAU_60S_NANOS)) {
            return null; // Not matured yet
        }

        double r1s = computeLogReturnForHorizon(baseNanos, baseMid, ForwardReturnTargets.TAU_1S_NANOS);
        double r5s = computeLogReturnForHorizon(baseNanos, baseMid, ForwardReturnTargets.TAU_5S_NANOS);
        double r10s = computeLogReturnForHorizon(baseNanos, baseMid, ForwardReturnTargets.TAU_10S_NANOS);
        double r30s = computeLogReturnForHorizon(baseNanos, baseMid, ForwardReturnTargets.TAU_30S_NANOS);
        double r60s = computeLogReturnForHorizon(baseNanos, baseMid, ForwardReturnTargets.TAU_60S_NANOS);

        return new ForwardReturnTargets(baseNanos, baseMid, r1s, r5s, r10s, r30s, r60s);
    }

    /**
     * Evaluates log return ln(P_mid(T_k + tau) / P_mid(T_k)) using LOCF search at T_k + tau.
     */
    private double computeLogReturnForHorizon(long baseNanos, double baseMid, long horizonNanos) {
        long targetNanos = baseNanos + horizonNanos;
        double forwardMid = findLOCFMidPrice(targetNanos);

        if (forwardMid <= 0.0) {
            return Double.NaN;
        }

        return Math.log(forwardMid / baseMid);
    }

    /**
     * Binary search to find the latest snapshot strictly at or before targetNanos (LOCF).
     */
    public synchronized double findLOCFMidPrice(long targetNanos) {
        if (history.isEmpty()) return Double.NaN;

        int low = 0;
        int high = history.size() - 1;
        int candidate = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midNanos = history.get(mid).gridNanos();

            if (midNanos <= targetNanos) {
                candidate = mid;
                low = mid + 1; // Look for even closer observation <= targetNanos
            } else {
                high = mid - 1;
            }
        }

        if (candidate >= 0) {
            return history.get(candidate).snapshot().midPrice();
        }

        return Double.NaN;
    }

    public synchronized int getHistorySize() { return history.size(); }
    public synchronized void reset() { history.clear(); }
}
