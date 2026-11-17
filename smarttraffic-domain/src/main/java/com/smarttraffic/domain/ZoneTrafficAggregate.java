package com.smarttraffic.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ZoneTrafficAggregate {
    private final String zoneId;
    private final TrafficDensityStrategy strategy;
    private final List<CongestionObserver> observers = new ArrayList<>();
    private CongestionState state = CongestionState.DESPEJADA;
    private double density;

    public ZoneTrafficAggregate(String zoneId, TrafficDensityStrategy strategy) {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be blank");
        }
        this.zoneId = zoneId;
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    public void addObserver(CongestionObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer must not be null"));
    }

    public ZoneTrafficSnapshot record(TrafficReading reading) {
        if (!zoneId.equals(reading.zoneId())) {
            throw new IllegalArgumentException("reading belongs to another zone");
        }
        double newDensity = Math.max(0.0, Math.min(1.0, strategy.calculate(reading)));
        CongestionState previous = state;
        state = transitionFor(state).resolve(newDensity);
        density = newDensity;
        if (previous != state && state == CongestionState.CONGESTIONADA) {
            observers.forEach(observer -> observer.onCriticalChange(zoneId, previous, state, density));
        }
        return snapshot();
    }

    public ZoneTrafficSnapshot snapshot() {
        return new ZoneTrafficSnapshot(zoneId, density, state);
    }

    private CongestionTransition transitionFor(CongestionState current) {
        return switch (current) {
            case DESPEJADA -> new ClearState();
            case MODERADA -> new ModerateState();
            case CONGESTIONADA -> new CongestedState();
        };
    }
}
