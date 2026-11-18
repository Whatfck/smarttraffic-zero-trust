package com.smarttraffic.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smarttraffic.domain.CongestionObserver;
import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.TrafficDensityStrategy;
import com.smarttraffic.domain.TrafficReading;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Capa de servicio aislada con Mockito: sin Spring, sin Kafka, sin RabbitMQ. */
@ExtendWith(MockitoExtension.class)
class TrafficProcessingServiceTest {
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    @Mock private TrafficDensityStrategy strategy;
    @Mock private TrafficStatePublisher publisher;
    @Mock private CongestionObserver observer;

    private TrafficProcessingService service;

    @BeforeEach
    void setUp() {
        service = new TrafficProcessingService(strategy, publisher, List.of(observer));
    }

    @Test
    @DisplayName("AAA: primera lectura de una zona crea el agregado y delega el calculo en la Strategy")
    void firstReadingCreatesAggregateUsingStrategy() {
        // Arrange
        var reading = new TrafficReading("north", 85, 10, T0);
        when(strategy.calculate(reading)).thenReturn(0.85);

        // Act
        ZoneTrafficSnapshot snapshot = service.aggregate("north", reading, null);

        // Assert
        assertEquals(CongestionState.CONGESTIONADA, snapshot.state());
        assertEquals(1, snapshot.readings());
        verify(strategy, times(1)).calculate(reading);
        verify(observer, times(1)).onCongestionChanged(any());
        verifyNoInteractions(publisher); // agregar no publica: eso lo hace la topologia
    }

    @Test
    @DisplayName("AAA: continua desde el estado previo de la KTable (stateful)")
    void continuesFromPreviousState() {
        var previous = new ZoneTrafficSnapshot("north", 0.8, CongestionState.CONGESTIONADA, 4, T0);
        var reading = new TrafficReading("north", 60, 30, T0.plusSeconds(3));
        when(strategy.calculate(reading)).thenReturn(0.60);

        var snapshot = service.aggregate("north", reading, previous);

        assertEquals(CongestionState.CONGESTIONADA, snapshot.state()); // histeresis
        assertEquals(5, snapshot.readings());
        verify(observer, never()).onCongestionChanged(any());
    }

    @Test
    @DisplayName("AAA: publish despacha hacia el broker exactamente una vez")
    void publishesExactlyOnce() {
        var snapshot = new ZoneTrafficSnapshot("north", 0.2, CongestionState.DESPEJADA, 1, T0);

        service.publish(snapshot);

        verify(publisher, times(1)).publish(snapshot);
        verifyNoMoreInteractions(publisher);
    }
}
