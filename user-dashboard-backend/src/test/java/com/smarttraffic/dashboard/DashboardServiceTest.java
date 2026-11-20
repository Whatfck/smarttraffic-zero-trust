package com.smarttraffic.dashboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    @Mock private DashboardBroadcaster broadcaster;

    @Test
    @DisplayName("AAA: mapea el mensaje AMQP a un DTO inmutable y lo emite por WebSocket una vez")
    void mapsAndBroadcastsOnce() {
        // Arrange
        var service = new DashboardService(new ZoneStatusViewMapper(), broadcaster);
        var snapshot = new ZoneTrafficSnapshot("north", 0.856, CongestionState.CONGESTIONADA, 12, T0);

        // Act
        service.onTrafficUpdate(snapshot);

        // Assert
        var captor = ArgumentCaptor.forClass(ZoneStatusView.class);
        verify(broadcaster, times(1)).broadcast(captor.capture());
        var view = captor.getValue();
        assertEquals("north", view.zoneId());
        assertEquals(86, view.densityPercent());
        assertEquals("red", view.color());
        assertEquals(1, service.currentViews().size());
    }

    @Test
    @DisplayName("AAA: conserva solo la ultima vista por zona")
    void keepsLatestViewPerZone() {
        var service = new DashboardService(new ZoneStatusViewMapper(), broadcaster);

        service.onTrafficUpdate(new ZoneTrafficSnapshot("north", 0.1, CongestionState.DESPEJADA, 1, T0));
        service.onTrafficUpdate(new ZoneTrafficSnapshot("north", 0.5, CongestionState.MODERADA, 2, T0));

        assertEquals(1, service.currentViews().size());
        assertEquals("MODERADA", service.currentViews().iterator().next().state());
    }

    @Test
    @DisplayName("Fail-Fast del DTO de presentacion")
    void viewFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new ZoneStatusView("n", 101, "MODERADA", "amber", 1, T0));
        assertThrows(NullPointerException.class,
                () -> new ZoneStatusView("n", 10, null, "amber", 1, T0));
    }
}
