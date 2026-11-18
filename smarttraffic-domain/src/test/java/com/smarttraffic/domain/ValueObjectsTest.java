package com.smarttraffic.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Fail-Fast en los constructores compactos de los records (Value Objects y contratos). */
class ValueObjectsTest {
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    @DisplayName("TrafficReading rechaza zona vacia/nula, valores negativos y fecha nula")
    void trafficReadingFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new TrafficReading(null, 1, 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> new TrafficReading(" ", 1, 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> new TrafficReading("n", -1, 1, NOW));
        assertThrows(IllegalArgumentException.class, () -> new TrafficReading("n", 1, -0.1, NOW));
        assertThrows(NullPointerException.class, () -> new TrafficReading("n", 1, 1, null));
        assertDoesNotThrow(() -> new TrafficReading("n", 0, 0, NOW));
    }

    @Test
    @DisplayName("TrafficDensity solo admite [0,1] y clamped normaliza")
    void trafficDensityFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new TrafficDensity(-0.01));
        assertThrows(IllegalArgumentException.class, () -> new TrafficDensity(1.01));
        assertThrows(IllegalArgumentException.class, () -> new TrafficDensity(Double.NaN));
        assertEquals(1.0, TrafficDensity.clamped(7).value());
        assertEquals(0.0, TrafficDensity.clamped(-3).value());
    }

    @Test
    @DisplayName("ZoneTrafficSnapshot rechaza densidad fuera de rango, estado nulo y contador negativo")
    void snapshotFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new ZoneTrafficSnapshot("n", 1.5, CongestionState.MODERADA, 1, NOW));
        assertThrows(NullPointerException.class, () -> new ZoneTrafficSnapshot("n", 0.5, null, 1, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new ZoneTrafficSnapshot("n", 0.5, CongestionState.MODERADA, -1, NOW));
    }

    @Test
    @DisplayName("RouteQuery exige requestId, clientId y al menos una zona; la lista es inmutable")
    void routeQueryFailsFastAndIsImmutable() {
        assertThrows(IllegalArgumentException.class, () -> new RouteQuery("", "c", List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> new RouteQuery("r", null, List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> new RouteQuery("r", "c", List.of()));
        var query = new RouteQuery("r", "c", new java.util.ArrayList<>(List.of("a")));
        assertThrows(UnsupportedOperationException.class, () -> query.zoneIds().add("b"));
    }

    @Test
    @DisplayName("ZoneId (identificador record) rechaza vacio, nulo y formatos inseguros")
    void zoneIdFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> ZoneId.of(null));
        assertThrows(IllegalArgumentException.class, () -> ZoneId.of("  "));
        assertThrows(IllegalArgumentException.class, () -> ZoneId.of("north; DROP TABLE"));
        assertEquals(ZoneId.of("north"), ZoneId.of("north"));
        assertThrows(IllegalArgumentException.class, () -> new TrafficReading("zona mala", 1, 1, NOW));
    }

    @Test
    @DisplayName("Las estrategias rechazan parametros no positivos")
    void strategiesFailFast() {
        assertThrows(IllegalArgumentException.class, () -> new VehicleDensityStrategy(0));
        assertThrows(IllegalArgumentException.class, () -> new SpeedDensityStrategy(-5));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedDensityStrategy(r -> 0, r -> 0, 1.5));
    }
}
