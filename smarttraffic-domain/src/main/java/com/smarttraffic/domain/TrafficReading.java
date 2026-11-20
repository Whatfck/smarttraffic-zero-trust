package com.smarttraffic.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object / contrato del mensaje publicado en Kafka (raw_traffic_data).
 * Inmutable (record) y con validacion Fail-Fast en el constructor compacto: es
 * imposible construir una lectura con zona vacia, conteos negativos o sin fecha.
 */
public record TrafficReading(String zoneId, int vehicles, double averageSpeed, Instant observedAt) {
    public TrafficReading {
        ZoneId.of(zoneId); // valida el identificador (no vacio, formato seguro)
        if (vehicles < 0) {
            throw new IllegalArgumentException("vehicles must not be negative");
        }
        if (averageSpeed < 0 || Double.isNaN(averageSpeed)) {
            throw new IllegalArgumentException("averageSpeed must not be negative");
        }
        Objects.requireNonNull(observedAt, "observedAt must not be null");
    }

    public boolean belongsTo(String otherZoneId) {
        return zoneId.equals(otherZoneId);
    }
}
