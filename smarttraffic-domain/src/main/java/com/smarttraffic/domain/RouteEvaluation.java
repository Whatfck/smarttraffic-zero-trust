package com.smarttraffic.domain;

import java.util.List;
import java.util.Objects;

/** Respuesta inmutable: trayecto evaluado (peor nivel del recorrido + detalle por zona). */
public record RouteEvaluation(String requestId, Status status, CongestionState overall,
                              List<ZoneTrafficSnapshot> zones, String error) {
    public enum Status { OK, ZONE_NOT_FOUND }

    public RouteEvaluation {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        Objects.requireNonNull(status, "status must not be null");
        zones = List.copyOf(Objects.requireNonNull(zones, "zones must not be null"));
    }

    public static RouteEvaluation ok(String requestId, List<ZoneTrafficSnapshot> zones) {
        CongestionState overall = zones.stream().map(ZoneTrafficSnapshot::state)
                .reduce(CongestionState.DESPEJADA, CongestionState::worst);
        return new RouteEvaluation(requestId, Status.OK, overall, zones, null);
    }

    public static RouteEvaluation zoneNotFound(String requestId, ZoneNotFoundException exception) {
        return new RouteEvaluation(requestId, Status.ZONE_NOT_FOUND, null, List.of(), exception.getMessage());
    }
}
