package com.smarttraffic.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoneTrafficAggregateTest {
    private static TrafficReading reading(int vehicles, double speed) {
        return new TrafficReading("north", vehicles, speed, Instant.EPOCH);
    }

    @Test
    @DisplayName("AAA: Aplica VehicleDensityStrategy y transiciona entre estados DESPEJADA -> MODERADA -> CONGESTIONADA")
    void appliesStrategyAndStateTransitions() {
        // Arrange
        var strategy = new VehicleDensityStrategy(100);
        var aggregate = new ZoneTrafficAggregate("north", strategy);

        // Act & Assert (DESPEJADA: density < 0.35)
        var snap1 = aggregate.record(reading(20, 40));
        assertEquals(CongestionState.DESPEJADA, snap1.state());
        assertEquals(0.20, snap1.density(), 0.001);

        // Act & Assert (MODERADA: 0.35 <= density < 0.70)
        var snap2 = aggregate.record(reading(50, 40));
        assertEquals(CongestionState.MODERADA, snap2.state());
        assertEquals(0.50, snap2.density(), 0.001);

        // Act & Assert (CONGESTIONADA: density >= 0.70)
        var snap3 = aggregate.record(reading(80, 40));
        assertEquals(CongestionState.CONGESTIONADA, snap3.state());
        assertEquals(0.80, snap3.density(), 0.001);
    }

    @Test
    @DisplayName("AAA: SpeedDensityStrategy calcula la densidad inversamente proporcional a la velocidad libre")
    void appliesSpeedDensityStrategy() {
        // Arrange: Velocidad libre = 80 km/h
        var strategy = new SpeedDensityStrategy(80.0);
        var reading = new TrafficReading("north", 30, 20.0, Instant.EPOCH); // 20 km/h -> densidad 1 - 20/80 = 0.75

        // Act
        double density = strategy.calculate(reading);

        // Assert
        assertEquals(0.75, density, 0.001);
    }

    @Test
    @DisplayName("AAA: Patrón Observer notifica de forma desacoplada únicamente al entrar en CONGESTIONADA")
    void notifiesOnlyWhenEnteringCriticalState() {
        // Arrange
        var observer = mock(CongestionObserver.class);
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));
        aggregate.addObserver(observer);

        // Act
        aggregate.record(reading(80, 40)); // Transiciona a CONGESTIONADA (primer disparo)
        aggregate.record(reading(90, 40)); // Sigue en CONGESTIONADA (no debe disparar de nuevo)

        // Assert
        verify(observer, times(1)).onCriticalChange("north", CongestionState.DESPEJADA,
                CongestionState.CONGESTIONADA, 0.8);
    }

    @Test
    @DisplayName("AAA: Fail-Fast rechaza lecturas con métricas negativas o zonas cruzadas")
    void rejectsInvalidReadingsAndForeignZones() {
        // Assert Fail-Fast en Value Object record
        assertThrows(IllegalArgumentException.class, () -> reading(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> reading(10, -5.0));
        assertThrows(IllegalArgumentException.class, () -> new TrafficReading("", 10, 10.0, Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> new TrafficReading("north", 10, 10.0, null));

        // Assert Fail-Fast en el agregado
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));
        assertThrows(IllegalArgumentException.class,
                () -> aggregate.record(new TrafficReading("south", 1, 10, Instant.EPOCH)));
    }
}
