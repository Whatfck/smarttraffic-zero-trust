package com.smarttraffic.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tabla completa de transiciones del patron State (incluye histeresis al bajar). */
class TrafficZoneStateTest {

    @ParameterizedTest(name = "{0} con densidad {1} -> {2}")
    @CsvSource({
            // desde DESPEJADA
            "DESPEJADA, 0.10, DESPEJADA",
            "DESPEJADA, 0.35, MODERADA",
            "DESPEJADA, 0.70, CONGESTIONADA",
            // desde MODERADA
            "MODERADA, 0.30, MODERADA",       // histeresis: no baja hasta < 0.25
            "MODERADA, 0.24, DESPEJADA",
            "MODERADA, 0.69, MODERADA",
            "MODERADA, 0.95, CONGESTIONADA",
            // desde CONGESTIONADA
            "CONGESTIONADA, 0.60, CONGESTIONADA", // histeresis: no baja hasta < 0.55
            "CONGESTIONADA, 0.54, MODERADA",
            "CONGESTIONADA, 0.10, DESPEJADA",
    })
    void transitions(CongestionState from, double density, CongestionState expected) {
        // Arrange
        TrafficZoneState state = TrafficZoneState.of(from);

        // Act
        TrafficZoneState next = state.next(new TrafficDensity(density));

        // Assert
        assertEquals(expected, next.level());
    }
}
