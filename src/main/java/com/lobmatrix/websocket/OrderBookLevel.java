package com.lobmatrix.websocket;

public record OrderBookLevel(
        double price,
        long quantity,
        int orders
) {
}
