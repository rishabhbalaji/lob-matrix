package com.lobmatrix.core.model;

/**
 * Represents the official trading session phases for NSE equities.
 */
public enum SessionPhase {
    PRE_OPEN_ORDER_ENTRY, // 09:00 - 09:08 IST
    PRE_OPEN_MATCHING,    // 09:08 - 09:15 IST
    CONTINUOUS_TRADING,   // 09:15 - 15:30 IST (Regular continuous market)
    POST_CLOSE,           // 15:30 - 16:00 IST (Closing price determination)
    CLOSED                // Market closed (overnight / weekend)
}
