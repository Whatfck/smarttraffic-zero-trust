package com.smarttraffic.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Pruebas de dominio puro (sin Spring, BD ni brokers) con estructura AAA. */
class ZoneTrafficAggregateTest {
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    private static TrafficReading reading(int vehicles) {
        return new TrafficReading("north", vehicles, 40.0, T0);
    }

    @Test
    @DisplayName("AAA: Strategy (vehiculos/capacidad) + State: DESPEJADA -> MODERADA -> CONGESTIONADA")
    void appliesStrategyAndStateTransitions() {
        // Arrange
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));

        // Act
        var clear = aggregate.record(reading(20));
        var moderate = aggregate.record(reading(50));
        var congested = aggregate.record(reading(80));

        // Assert
        assertEquals(CongestionState.DESPEJADA, clear.state());
        assertEquals(0.20, clear.density(), 1e-9);
        assertEquals(CongestionState.MODERADA, moderate.state());
        assertEquals(CongestionState.CONGESTIONADA, congested.state());
        assertEquals(3, congested.readings());
    }

    @Test
    @DisplayName("AAA: intercambiar la Strategy cambia el calculo sin tocar el agregado")
    void strategyIsInterchangeable() {
        // Arrange: misma lectura, dos algoritmos
        var lecture = new TrafficReading("north", 10, 20.0, T0);
        var byVehicles = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));
        var bySpeed = new ZoneTrafficAggregate("north", new SpeedDensityStrategy(80));

        // Act
        double vehicles = byVehicles.record(lecture).density();
        double speed = bySpeed.record(lecture).density();

        // Assert
        assertEquals(0.10, vehicles, 1e-9);
        assertEquals(0.75, speed, 1e-9);
    }

    @Test
    @DisplayName("AAA: la densidad se normaliza a [0,1] aunque la estrategia se desborde")
    void densityIsAlwaysNormalized() {
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(10));

        var snapshot = aggregate.record(reading(500));

        assertEquals(1.0, snapshot.density(), 1e-9);
    }

    @Test
    @DisplayName("AAA: Observer recibe un evento solo cuando cambia el nivel")
    void notifiesObserversOnlyOnLevelChange() {
        // Arrange
        var observer = mock(CongestionObserver.class);
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));
        aggregate.subscribe(observer);

        // Act
        aggregate.record(reading(80)); // DESPEJADA -> CONGESTIONADA (evento)
        aggregate.record(reading(90)); // sigue CONGESTIONADA (sin evento)

        // Assert
        var captor = ArgumentCaptor.forClass(CongestionChangedEvent.class);
        verify(observer, times(1)).onCongestionChanged(captor.capture());
        assertTrue(captor.getValue().enteredCritical());
        assertEquals(CongestionState.DESPEJADA, captor.getValue().previous());
    }

    @Test
    @DisplayName("AAA: invariante - no acepta lecturas de otra zona y no notifica nada")
    void rejectsReadingsFromAnotherZone() {
        var observer = mock(CongestionObserver.class);
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));
        aggregate.subscribe(observer);

        assertThrows(IllegalArgumentException.class,
                () -> aggregate.record(new TrafficReading("south", 99, 5, T0)));
        verify(observer, never()).onCongestionChanged(any());
        assertEquals(0, aggregate.snapshot().readings());
    }

    @Test
    @DisplayName("AAA: rehidratar desde un snapshot conserva estado, densidad y contador")
    void rehydratesFromSnapshot() {
        var stored = new ZoneTrafficSnapshot("north", 0.8, CongestionState.CONGESTIONADA, 7, T0);

        var aggregate = ZoneTrafficAggregate.rehydrate(stored, new VehicleDensityStrategy(100));
        var next = aggregate.record(reading(60)); // 0.60 >= 0.55 -> sigue CONGESTIONADA (histeresis)

        assertEquals(CongestionState.CONGESTIONADA, next.state());
        assertEquals(8, next.readings());
    }
}
