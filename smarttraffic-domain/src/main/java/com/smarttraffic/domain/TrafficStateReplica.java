package com.smarttraffic.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replica local (en memoria) del mapa de estados de la ciudad, alimentada por el
 * fanout traffic_updates. Es SUJETO del patron Observer: avisa a sus observadores
 * cuando una zona cambia de nivel, sin saber quien escucha (alertas, logs, etc.).
 */
public final class TrafficStateReplica {
    private final Map<ZoneId, ZoneTrafficSnapshot> zones = new ConcurrentHashMap<>();
    private final List<CongestionObserver> observers = new CopyOnWriteArrayList<>();

    public void subscribe(CongestionObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer must not be null"));
    }

    /** Aplica una actualizacion; descarta fotos mas viejas que la que ya tiene. */
    public void apply(ZoneTrafficSnapshot update) {
        Objects.requireNonNull(update, "update must not be null");
        ZoneId id = ZoneId.of(update.zoneId());
        ZoneTrafficSnapshot previous = zones.get(id);
        if (previous != null && update.updatedAt().isBefore(previous.updatedAt())) {
            return;
        }
        zones.put(id, update);
        CongestionState before = previous == null ? CongestionState.DESPEJADA : previous.state();
        if (before != update.state()) {
            var event = new CongestionChangedEvent(update.zoneId(), before, update.state(),
                    update.density(), update.updatedAt());
            observers.forEach(observer -> observer.onCongestionChanged(event));
        }
    }

    public ZoneTrafficSnapshot zone(String zoneId) {
        ZoneId id;
        try {
            id = ZoneId.of(zoneId);
        } catch (IllegalArgumentException invalid) {
            throw new ZoneNotFoundException(zoneId);
        }
        return Optional.ofNullable(zones.get(id)).orElseThrow(() -> new ZoneNotFoundException(zoneId));
    }

    /** Evalua un trayecto; lanza ZoneNotFoundException si alguna zona no existe. */
    public RouteEvaluation evaluate(RouteQuery query) {
        List<ZoneTrafficSnapshot> route = new ArrayList<>();
        query.zoneIds().forEach(zoneId -> route.add(zone(zoneId)));
        return RouteEvaluation.ok(query.requestId(), route);
    }

    public List<ZoneTrafficSnapshot> all() {
        return List.copyOf(zones.values());
    }
}
