package com.smarttraffic.domain;

/** Densidad = vehiculos observados / capacidad de la via. */
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
        return reading.vehicles() / roadCapacity;
    }
}
