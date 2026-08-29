package com.lobmatrix.core.adapter;

/**
 * Lifecycle connection state of a market data feed.
 */
public enum FeedStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}
