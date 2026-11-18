package com.smarttraffic.domain;

/** Densidad inversa a la velocidad: 1 - velocidad / velocidad de flujo libre. */
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
        return 1.0 - reading.averageSpeed() / freeFlowSpeed;
    }
}
