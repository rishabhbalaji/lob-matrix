package com.lobmatrix.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class OrderBookWebSocketConfig implements WebSocketConfigurer {

    private final OrderBookWebSocketHandler orderBookWebSocketHandler;

    public OrderBookWebSocketConfig(OrderBookWebSocketHandler orderBookWebSocketHandler) {
        this.orderBookWebSocketHandler = orderBookWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderBookWebSocketHandler, "/ws/orderbook");
    }
}
