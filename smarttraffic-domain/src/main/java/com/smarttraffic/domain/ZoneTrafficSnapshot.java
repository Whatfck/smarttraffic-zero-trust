package com.smarttraffic.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Contrato inmutable del estado consolidado de una zona. Es lo que se guarda en la
 * KTable, se publica en el fanout traffic_updates y se replica en vm-app.
 */
public record ZoneTrafficSnapshot(String zoneId, double density, CongestionState state,
                                  long readings, Instant updatedAt) {
    public ZoneTrafficSnapshot {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be blank");
        }
        new TrafficDensity(density); // valida rango 0..1 (fail-fast)
        Objects.requireNonNull(state, "state must not be null");
        if (readings < 0) {
            throw new IllegalArgumentException("readings must not be negative");
        }
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
