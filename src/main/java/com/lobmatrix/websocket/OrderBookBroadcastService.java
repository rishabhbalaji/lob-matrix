package com.lobmatrix.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Service
public class OrderBookBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(OrderBookBroadcastService.class);

    private final OrderBookWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public OrderBookBroadcastService(OrderBookWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    public void broadcast(CanonicalMarketSnapshot snapshot) {
        if (handler.sessions().isEmpty()) {
            return;
        }

        final String payload;
        try {
            payload = objectMapper.writeValueAsString(OrderBookSnapshotMessage.from(snapshot));
        } catch (JsonProcessingException exception) {
            log.error("Unable to serialize order book snapshot for token={}", snapshot.instrumentToken(), exception);
            return;
        }

        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : handler.sessions()) {
            if (!session.isOpen()) {
                handler.sessions().remove(session);
                continue;
            }

            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                }
            } catch (IOException | RuntimeException exception) {
                handler.sessions().remove(session);
                log.warn("Unable to deliver order book update to session={}; removing session",
                        session.getId(), exception);
            }
        }
    }
}
