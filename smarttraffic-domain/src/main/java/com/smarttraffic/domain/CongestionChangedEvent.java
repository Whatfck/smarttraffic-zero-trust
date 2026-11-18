package com.smarttraffic.domain;

import java.time.Instant;
import java.util.Objects;

/** Evento de dominio inmutable: una zona cambio de nivel de congestion. */
public record CongestionChangedEvent(String zoneId, CongestionState previous, CongestionState current,
                                     double density, Instant occurredAt) {
    public CongestionChangedEvent {
        ZoneId.of(zoneId); // valida el identificador (no vacio, formato seguro)
        Objects.requireNonNull(previous, "previous must not be null");
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public boolean enteredCritical() {
        return current.isCritical() && !previous.isCritical();
    }

    public boolean leftCritical() {
        return previous.isCritical() && !current.isCritical();
    }
}
