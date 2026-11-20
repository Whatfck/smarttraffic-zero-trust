package com.smarttraffic.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Replica local de vm-app: Observer, consultas de trayecto y excepcion de dominio. */
class TrafficStateReplicaTest {
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    private static ZoneTrafficSnapshot snap(String zone, CongestionState state, Instant at) {
        return new ZoneTrafficSnapshot(zone, 0.5, state, 1, at);
    }

    @Test
    @DisplayName("AAA: notifica al Observer cuando una zona cambia de nivel, no cuando se repite")
    void notifiesOnLevelChange() {
        // Arrange
        var observer = mock(CongestionObserver.class);
        var replica = new TrafficStateReplica();
        replica.subscribe(observer);

        // Act
        replica.apply(snap("north", CongestionState.CONGESTIONADA, T0));
        replica.apply(snap("north", CongestionState.CONGESTIONADA, T0.plusSeconds(1)));

        // Assert
        verify(observer, times(1)).onCongestionChanged(any());
    }

    @Test
    @DisplayName("AAA: descarta actualizaciones desordenadas (mas viejas que la actual)")
    void ignoresOutOfOrderUpdates() {
        var replica = new TrafficStateReplica();
        replica.apply(snap("north", CongestionState.MODERADA, T0.plusSeconds(10)));

        replica.apply(snap("north", CongestionState.DESPEJADA, T0));

        assertEquals(CongestionState.MODERADA, replica.zone("north").state());
    }

    @Test
    @DisplayName("AAA: evalua un trayecto con el peor nivel del recorrido")
    void evaluatesRouteWithWorstLevel() {
        var replica = new TrafficStateReplica();
        replica.apply(snap("north", CongestionState.DESPEJADA, T0));
        replica.apply(snap("center", CongestionState.CONGESTIONADA, T0));

        var evaluation = replica.evaluate(new RouteQuery("r1", "client-1", List.of("north", "center")));

        assertEquals(RouteEvaluation.Status.OK, evaluation.status());
        assertEquals(CongestionState.CONGESTIONADA, evaluation.overall());
        assertEquals(2, evaluation.zones().size());
    }

    @Test
    @DisplayName("AAA: lanza ZoneNotFoundException (excepcion de dominio) ante zona inexistente")
    void throwsDomainExceptionForUnknownZone() {
        var replica = new TrafficStateReplica();
        replica.apply(snap("north", CongestionState.DESPEJADA, T0));

        var exception = assertThrows(ZoneNotFoundException.class,
                () -> replica.evaluate(new RouteQuery("r1", "client-1", List.of("north", "atlantis"))));
        assertEquals("atlantis", exception.zoneId());
    }
}
