package com.smarttraffic.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.RouteEvaluation;
import com.smarttraffic.domain.RouteQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteQueryClientTest {
    @Mock private RouteQuerySender sender;

    @Test
    @DisplayName("AAA: envia la consulta una vez con su identidad y la marca como pendiente")
    void asksOnceAndTracksPending() {
        // Arrange
        var identity = new ClientIdentity("qc-1");
        var client = new RouteQueryClient(identity, sender, () -> List.of("north", "south"),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        // Act
        RouteQuery query = client.ask();

        // Assert
        var captor = ArgumentCaptor.forClass(RouteQuery.class);
        verify(sender, times(1)).send(captor.capture());
        assertEquals("qc-1", captor.getValue().clientId());
        assertEquals(List.of("north", "south"), captor.getValue().zoneIds());
        assertEquals(1, client.pendingCount());
        assertEquals(query, captor.getValue());
    }

    @Test
    @DisplayName("AAA: al recibir la respuesta correlaciona por requestId y libera la pendiente")
    void correlatesAnswer() {
        var client = new RouteQueryClient(new ClientIdentity("qc-1"), sender, () -> List.of("north"),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var query = client.ask();

        var latency = client.onAnswer(RouteEvaluation.ok(query.requestId(), List.of()));

        assertTrue(latency.isPresent());
        assertEquals(0, client.pendingCount());
        assertEquals(CongestionState.DESPEJADA, RouteEvaluation.ok("x", List.of()).overall());
    }

    @Test
    @DisplayName("Cada replica tiene cola y routing key propias")
    void identityDerivesQueueAndRoutingKey() {
        var identity = new ClientIdentity("qc-abc");
        assertEquals("query_client.qc-abc", identity.replyQueue());
        assertEquals("query.qc-abc", identity.routingKey());
        assertThrows(IllegalArgumentException.class, () -> new ClientIdentity(" "));
    }
}
