package com.smarttraffic.dashboard;

import java.time.Instant;
import java.util.Objects;

/**
 * DTO inmutable de PRESENTACION que viaja por WebSocket al navegador. Se separa del
 * contrato de mensajeria (ZoneTrafficSnapshot) para que cambiar la UI no obligue a
 * cambiar los mensajes entre microservicios.
 */
public record ZoneStatusView(String zoneId, int densityPercent, String state, String color,
                             long readings, Instant updatedAt) {
    public ZoneStatusView {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be blank");
        }
        if (densityPercent < 0 || densityPercent > 100) {
            throw new IllegalArgumentException("densityPercent must be between 0 and 100");
        }
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(color, "color must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
