package com.smarttraffic.domain;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Regla de transicion declarativa: "si la densidad cumple X, pasar al estado Y". */
public record TransitionRule(Predicate<TrafficDensity> condition, Supplier<TrafficZoneState> target) {
    public TransitionRule {
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(target, "target must not be null");
    }

    public static TransitionRule when(Predicate<TrafficDensity> condition, Supplier<TrafficZoneState> target) {
        return new TransitionRule(condition, target);
    }

    public boolean appliesTo(TrafficDensity density) {
        return condition.test(density);
    }
}
