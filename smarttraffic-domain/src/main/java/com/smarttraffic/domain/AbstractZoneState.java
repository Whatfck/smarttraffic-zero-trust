package com.smarttraffic.domain;

import java.util.List;

/**
 * Comportamiento comun del patron State: recorre las reglas del estado concreto y
 * toma la primera que aplique; si ninguna aplica, permanece en el estado actual.
 * (Template Method: las subclases solo declaran sus reglas.)
 */
abstract class AbstractZoneState implements TrafficZoneState {

    protected abstract List<TransitionRule> rules();

    @Override
    public final TrafficZoneState next(TrafficDensity density) {
        return rules().stream()
                .filter(rule -> rule.appliesTo(density))
                .findFirst()
                .map(rule -> rule.target().get())
                .orElse(this);
    }

    @Override
    public String toString() {
        return level().name();
    }
}
