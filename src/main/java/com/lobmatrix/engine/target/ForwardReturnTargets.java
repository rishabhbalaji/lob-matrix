package com.lobmatrix.engine.target;

/**
 * Immutable carrier holding multi-horizon forward mid-price log returns R_{mid, tau}(T_k) = ln(P_mid(T_k + tau) / P_mid(T_k)).
 */
public record ForwardReturnTargets(
        long baseGridNanos,      // Timestamp T_k when features were computed
        double baseMidPrice,     // P_mid(T_k)
        double return1s,         // R_{mid, 1s}
        double return5s,         // R_{mid, 5s}
        double return10s,        // R_{mid, 10s}
        double return30s,        // R_{mid, 30s}
        double return60s         // R_{mid, 60s}
) {
    public static final long TAU_1S_NANOS = 1_000_000_000L;
    public static final long TAU_5S_NANOS = 5_000_000_000L;
    public static final long TAU_10S_NANOS = 10_000_000_000L;
    public static final long TAU_30S_NANOS = 30_000_000_000L;
    public static final long TAU_60S_NANOS = 60_000_000_000L;

    public static final long[] STANDARD_HORIZONS_NANOS = {
            TAU_1S_NANOS, TAU_5S_NANOS, TAU_10S_NANOS, TAU_30S_NANOS, TAU_60S_NANOS
    };
}
