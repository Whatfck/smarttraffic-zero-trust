package com.smarttraffic.domain;

/**
 * Patron STRATEGY: algoritmo intercambiable para calcular la densidad de una lectura.
 * El consumidor (traffic_density_processor) solo conoce esta interfaz; la estrategia
 * concreta se elige por configuracion sin tocar la logica del consumidor.
 */
@FunctionalInterface
public interface TrafficDensityStrategy {
    /** Densidad cruda; el agregado la normaliza con {@link TrafficDensity#clamped(double)}. */
    double calculate(TrafficReading reading);
}
