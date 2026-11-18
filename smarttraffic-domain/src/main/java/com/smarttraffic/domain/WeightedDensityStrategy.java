package com.smarttraffic.domain;

import java.util.Objects;

/** Combina dos estrategias con un peso (composicion de estrategias). */
public final class WeightedDensityStrategy implements TrafficDensityStrategy {
    private final TrafficDensityStrategy first;
    private final TrafficDensityStrategy second;
    private final double firstWeight;

    public WeightedDensityStrategy(TrafficDensityStrategy first, TrafficDensityStrategy second, double firstWeight) {
        if (firstWeight < 0 || firstWeight > 1) {
            throw new IllegalArgumentException("firstWeight must be between 0 and 1");
        }
        this.first = Objects.requireNonNull(first, "first strategy must not be null");
        this.second = Objects.requireNonNull(second, "second strategy must not be null");
        this.firstWeight = firstWeight;
    }

    @Override
    public double calculate(TrafficReading reading) {
        double a = TrafficDensity.clamped(first.calculate(reading)).value();
        double b = TrafficDensity.clamped(second.calculate(reading)).value();
        return firstWeight * a + (1 - firstWeight) * b;
    }
}
