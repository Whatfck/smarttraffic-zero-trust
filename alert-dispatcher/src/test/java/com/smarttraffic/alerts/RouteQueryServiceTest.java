package com.smarttraffic.alerts;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.smarttraffic.domain.CongestionChangedEvent;
import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.RouteEvaluation;
import com.smarttraffic.domain.RouteQuery;
import com.smarttraffic.domain.TrafficStateReplica;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RouteQueryServiceTest {
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    @Mock private RouteAnswerPublisher publisher;
    private TrafficStateReplica replica;
    private CongestionAlertNotifier notifier;
    private RouteQueryService service;

    @BeforeEach
    void setUp() {
        notifier = new CongestionAlertNotifier();
        replica = new TrafficStateReplica();
        replica.subscribe(notifier);
        service = new RouteQueryService(replica, publisher);
    }

    @Test
    @DisplayName("AAA: responde un trayecto con el peor estado y publica la respuesta al cliente una vez")
    void answersRouteQuery() {
        // Arrange
        service.onTrafficUpdate(new ZoneTrafficSnapshot("north", 0.1, CongestionState.DESPEJADA, 1, T0));
        service.onTrafficUpdate(new ZoneTrafficSnapshot("center", 0.6, CongestionState.MODERADA, 1, T0));

        // Act
        var evaluation = service.answer(new RouteQuery("r-1", "qc-a", List.of("north", "center")));

        // Assert
        assertEquals(RouteEvaluation.Status.OK, evaluation.status());
        assertEquals(CongestionState.MODERADA, evaluation.overall());
        verify(publisher, times(1)).answer(eq("qc-a"), eq(evaluation));
    }

    @Test
    @DisplayName("AAA: zona inexistente -> excepcion de dominio controlada y respuesta ZONE_NOT_FOUND")
    void unknownZoneIsControlled() {
        service.onTrafficUpdate(new ZoneTrafficSnapshot("north", 0.1, CongestionState.DESPEJADA, 1, T0));

        var evaluation = service.answer(new RouteQuery("r-2", "qc-b", List.of("north", "atlantis")));

        assertEquals(RouteEvaluation.Status.ZONE_NOT_FOUND, evaluation.status());
        assertTrue(evaluation.error().contains("atlantis"));
        verify(publisher, times(1)).answer("qc-b", evaluation);
    }

    @Test
    @DisplayName("AAA: el Observer genera alerta solo al cruzar el umbral critico")
    void observerAlertsOnlyWhenCrossingThreshold() {
        service.onTrafficUpdate(new ZoneTrafficSnapshot("north", 0.5, CongestionState.MODERADA, 1, T0));
        service.onTrafficUpdate(new ZoneTrafficSnapshot("north", 0.8, CongestionState.CONGESTIONADA, 2, T0.plusSeconds(1)));
        service.onTrafficUpdate(new ZoneTrafficSnapshot("north", 0.9, CongestionState.CONGESTIONADA, 3, T0.plusSeconds(2)));

        List<CongestionChangedEvent> alerts = notifier.recentAlerts();
        assertEquals(1, alerts.size());
        assertTrue(alerts.get(0).enteredCritical());
    }

    @Test
    @DisplayName("AAA: el Observer se invoca a traves de la replica sin acoplamiento (mock)")
    void replicaNotifiesAnyObserver() {
        var observer = mock(com.smarttraffic.domain.CongestionObserver.class);
        replica.subscribe(observer);

        service.onTrafficUpdate(new ZoneTrafficSnapshot("south", 0.9, CongestionState.CONGESTIONADA, 1, T0));

        var captor = ArgumentCaptor.forClass(CongestionChangedEvent.class);
        verify(observer, times(1)).onCongestionChanged(captor.capture());
        assertEquals("south", captor.getValue().zoneId());
    }
}
