package com.lobmatrix.engine.math;

/**
 * Aggressor trade side classification under the Lee-Ready microstructure rule.
 */
public enum AggressorSide {
    BUY,       // Buyer-initiated taker trade (crossed ask or uptick)
    SELL,      // Seller-initiated taker trade (crossed bid or downtick)
    UNKNOWN    // Indeterminate / neutral
}
