package com.smarttraffic.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Agregado de dominio RICO de una zona de trafico.
 *
 * - Encapsula su estado (sin setters): solo cambia mediante el metodo de negocio
 *   {@link #record(TrafficReading)}, que protege las invariantes (la lectura debe
 *   ser de esta zona, la densidad siempre queda normalizada, el contador solo sube).
 * - Usa STRATEGY para calcular la densidad y STATE para la transicion de nivel.
 * - Es el SUJETO del patron OBSERVER: notifica cambios de nivel a sus observadores.
 */
public final class ZoneTrafficAggregate {
    private final ZoneId zoneId;
    private final TrafficDensityStrategy strategy;
    private final List<CongestionObserver> observers = new ArrayList<>();
    private TrafficZoneState state;
    private TrafficDensity density;
    private long readings;
    private Instant updatedAt;

    public ZoneTrafficAggregate(String zoneId, TrafficDensityStrategy strategy) {
        this(zoneId, strategy, new ClearZoneState(), new TrafficDensity(0.0), 0, Instant.EPOCH);
    }

    private ZoneTrafficAggregate(String zoneId, TrafficDensityStrategy strategy, TrafficZoneState state,
                                 TrafficDensity density, long readings, Instant updatedAt) {
        this.zoneId = ZoneId.of(zoneId);   // identificador validado (Value Object)
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.density = Objects.requireNonNull(density, "density must not be null");
        this.readings = readings;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** Reconstruye el agregado desde su ultima foto (p. ej. almacenada en la KTable). */
    public static ZoneTrafficAggregate rehydrate(ZoneTrafficSnapshot snapshot, TrafficDensityStrategy strategy) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return new ZoneTrafficAggregate(snapshot.zoneId(), strategy, TrafficZoneState.of(snapshot.state()),
                new TrafficDensity(snapshot.density()), snapshot.readings(), snapshot.updatedAt());
    }

    public void subscribe(CongestionObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer must not be null"));
    }

    /** Metodo de negocio: incorpora una lectura y devuelve el nuevo estado consolidado. */
    public ZoneTrafficSnapshot record(TrafficReading reading) {
        Objects.requireNonNull(reading, "reading must not be null");
        if (!reading.belongsTo(zoneId.value())) {
            throw new IllegalArgumentException("reading of zone " + reading.zoneId()
                    + " cannot be recorded in zone " + zoneId);
        }
        CongestionState previous = state.level();
        density = TrafficDensity.clamped(strategy.calculate(reading));
        state = state.next(density);
        readings++;
        updatedAt = reading.observedAt();

        CongestionState current = state.level();
        if (previous != current) {
            var event = new CongestionChangedEvent(zoneId.value(), previous, current, density.value(), updatedAt);
            observers.forEach(observer -> observer.onCongestionChanged(event));
        }
        return snapshot();
    }

    public ZoneTrafficSnapshot snapshot() {
        return new ZoneTrafficSnapshot(zoneId.value(), density.value(), state.level(), readings, updatedAt);
    }

    public String zoneId() {
        return zoneId.value();
    }

    public ZoneId id() {
        return zoneId;
    }

    public CongestionState level() {
        return state.level();
    }
}
