package com.smarttraffic.domain;

/**
 * Patron OBSERVER: quien quiera enterarse de cambios de congestion se suscribe sin
 * que el sujeto (agregado o replica) conozca su implementacion (log, broker, email...).
 */
@FunctionalInterface
public interface CongestionObserver {
    void onCongestionChanged(CongestionChangedEvent event);
}
