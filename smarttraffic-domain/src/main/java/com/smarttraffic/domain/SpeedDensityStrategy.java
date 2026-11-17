package com.smarttraffic.domain;

public final class SpeedDensityStrategy implements TrafficDensityStrategy {
    private final double freeFlowSpeed;

    public SpeedDensityStrategy(double freeFlowSpeed) {
        if (freeFlowSpeed <= 0) {
            throw new IllegalArgumentException("freeFlowSpeed must be positive");
        }
        this.freeFlowSpeed = freeFlowSpeed;
    }

    @Override
    public double calculate(TrafficReading reading) {
        return Math.min(1.0, Math.max(0.0, 1.0 - reading.averageSpeed() / freeFlowSpeed));
    }
}
