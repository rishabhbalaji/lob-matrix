package com.lobmatrix.engine.target;

/**
 * Computes Horizon-Scaled Volatility Thresholds theta_tau and classifies forward returns
 * into tri-class directional labels Y_tau in {-1, 0, +1}.
 */
public class VolatilityThresholdClassifier {

    /**
     * Calculates horizon-scaled volatility threshold:
     * sigma_tau = sigma_delta_t * sqrt(tau / delta_t)
     * theta_tau = max(alpha * sigma_tau, spread / (2 * P_mid))
     *
     * @param sigmaDeltaT Realized volatility at grid resolution delta_t (e.g. 1-sec rolling std dev of log returns)
     * @param deltaNanos Grid step resolution (e.g. 1_000_000_000L for 1s)
     * @param tauNanos Target forward horizon (e.g. 5_000_000_000L for 5s)
     * @param spread Absolute bid-ask spread at T_k
     * @param midPrice Mid-price at T_k
     * @param alpha Multiplier on volatility (standard default = 0.5)
     * @return Volatility threshold theta_tau in decimal percentage (e.g. 0.0005 = 5 bps)
     */
    public static double computeThreshold(double sigmaDeltaT, long deltaNanos, long tauNanos, 
                                          double spread, double midPrice, double alpha) {
        if (deltaNanos <= 0 || tauNanos <= 0) {
            return 0.0;
        }

        double timeRatio = (double) tauNanos / (double) deltaNanos;
        double sigmaTau = Math.max(0.0, sigmaDeltaT) * Math.sqrt(timeRatio);

        double halfRelativeSpread = 0.0;
        if (midPrice > 0.0 && spread > 0.0) {
            halfRelativeSpread = spread / (2.0 * midPrice);
        }

        return Math.max(alpha * sigmaTau, halfRelativeSpread);
    }

    public static double computeDefaultThreshold(double sigmaDeltaT, long deltaNanos, long tauNanos, double spread, double midPrice) {
        return computeThreshold(sigmaDeltaT, deltaNanos, tauNanos, spread, midPrice, 0.5);
    }

    /**
     * Classifies forward return into tri-class DirectionalLabel {-1, 0, +1} using threshold theta_tau.
     *
     * @param logReturn Forward return R_{mid, tau}
     * @param threshold Volatility threshold theta_tau
     * @return DirectionalLabel.UP (+1), DOWN (-1), or NEUTRAL (0)
     */
    public static DirectionalLabel classifyDirection(double logReturn, double threshold) {
        if (Double.isNaN(logReturn) || Double.isNaN(threshold)) {
            return DirectionalLabel.NEUTRAL;
        }

        if (logReturn > threshold) {
            return DirectionalLabel.UP;
        } else if (logReturn < -threshold) {
            return DirectionalLabel.DOWN;
        } else {
            return DirectionalLabel.NEUTRAL;
        }
    }
}
