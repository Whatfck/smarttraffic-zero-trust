package com.smarttraffic.gateway;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class TrafficUpdateListenerTest {

    @Mock
    private SimpMessagingTemplate websocket;

    @Mock
    private TrafficStateReplica replica;

    @Mock
    private AlertDispatcher alerts;

    @Test
    @DisplayName("AAA & TDD: Al recibir actualizacion AMQP, actualiza replica local, notifica alertas y emite WebSocket")
    void forwardsSnapshotToReplicaAlertsAndWebSocket() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();
        var listener = new TrafficUpdateListener(mapper, websocket, replica, alerts);
        var snapshot = new ZoneTrafficSnapshot("north", 0.85, CongestionState.CONGESTIONADA);
        String payload = mapper.writeValueAsString(snapshot);

        // Act
        listener.forward(payload);

        // Assert
        verify(replica, times(1)).put(eq(snapshot));
        verify(alerts, times(1)).observe(eq(snapshot));
        verify(websocket, times(1)).convertAndSend(eq("/topic/traffic"), eq(snapshot));
        verifyNoMoreInteractions(websocket);
    }
}
