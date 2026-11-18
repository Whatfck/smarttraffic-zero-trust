package com.smarttraffic.domain;

import java.util.List;
import java.util.Objects;

/** Contrato inmutable de una consulta asincrona: estado de un trayecto (lista de zonas). */
public record RouteQuery(String requestId, String clientId, List<String> zoneIds) {
    public RouteQuery {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        zoneIds = List.copyOf(Objects.requireNonNull(zoneIds, "zoneIds must not be null"));
        if (zoneIds.isEmpty()) {
            throw new IllegalArgumentException("a route needs at least one zone");
        }
    }
}
