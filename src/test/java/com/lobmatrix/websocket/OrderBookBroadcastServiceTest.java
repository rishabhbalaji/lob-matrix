package com.lobmatrix.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lobmatrix.core.model.BookStateTag;
import com.lobmatrix.core.model.CanonicalMarketSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderBookBroadcastServiceTest {

    @Test
    void dispatchSendsSerializedSnapshotToOpenSession() throws IOException {
        OrderBookWebSocketHandler handler = new OrderBookWebSocketHandler();
        ObjectMapper objectMapper = new ObjectMapper();
        OrderBookBroadcastService service = new OrderBookBroadcastService(handler, objectMapper);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        handler.sessions().add(session);

        service.dispatch(snapshot());

        verify(session).sendMessage(new TextMessage(
                objectMapper.writeValueAsString(OrderBookSnapshotMessage.from(snapshot()))
        ));
    }

    @Test
    void dispatchRemovesClosedSessionWithoutAttemptingDelivery() {
        OrderBookWebSocketHandler handler = new OrderBookWebSocketHandler();
        OrderBookBroadcastService service = new OrderBookBroadcastService(handler, new ObjectMapper());

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        handler.sessions().add(session);

        service.dispatch(snapshot());

        assertThat(handler.activeSessionCount()).isZero();
    }

    private static CanonicalMarketSnapshot snapshot() {
        return new CanonicalMarketSnapshot(
                "MOCK", 1001L, "SYM_1001", 10L, 1_700_000_000_000_000L, 1_700_000_000L,
                2500.25, 50L, 125_000L, 2500.10, 1,
                new double[]{2500.00}, new long[]{900L}, new int[]{4},
                new double[]{2500.50}, new long[]{800L}, new int[]{2},
                BookStateTag.NORMAL
        );
    }
}
