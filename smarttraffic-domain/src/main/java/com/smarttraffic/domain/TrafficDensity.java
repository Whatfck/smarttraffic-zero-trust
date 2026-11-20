package com.smarttraffic.domain;

/** Value Object: densidad vehicular normalizada entre 0.0 (vacia) y 1.0 (saturada). */
public record TrafficDensity(double value) {
    public TrafficDensity {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("density must be between 0.0 and 1.0 but was " + value);
        }
    }

    /** Normaliza el resultado crudo de una estrategia al rango valido. */
    public static TrafficDensity clamped(double raw) {
        return new TrafficDensity(Math.max(0.0, Math.min(1.0, Double.isNaN(raw) ? 0.0 : raw)));
    }

    public boolean atLeast(double threshold) {
        return value >= threshold;
    }

    public boolean below(double threshold) {
        return value < threshold;
    }
}
