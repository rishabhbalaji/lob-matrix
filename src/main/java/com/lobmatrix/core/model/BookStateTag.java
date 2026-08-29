package com.lobmatrix.core.model;

/**
 * Microstructure classification tags for the order book state.
 */
public enum BookStateTag {
    NORMAL,            // Best Bid < Best Ask (Standard liquid order book)
    STATE_LOCKED,      // Best Bid == Best Ask (Zero spread anomaly)
    STATE_CROSSED,     // Best Bid > Best Ask (Crossed book arbitrage anomaly)
    STATE_EMPTY_SIDE   // Zero bids or zero asks present
}
