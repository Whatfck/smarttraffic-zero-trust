package com.smarttraffic.domain;

public final class VehicleDensityStrategy implements TrafficDensityStrategy {
    private final double roadCapacity;

    public VehicleDensityStrategy(double roadCapacity) {
        if (roadCapacity <= 0) {
            throw new IllegalArgumentException("roadCapacity must be positive");
        }
        this.roadCapacity = roadCapacity;
    }

    @Override
    public double calculate(TrafficReading reading) {
        return Math.min(1.0, reading.vehicles() / roadCapacity);
    }
}
