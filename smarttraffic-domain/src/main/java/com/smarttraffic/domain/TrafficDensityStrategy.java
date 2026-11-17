package com.smarttraffic.domain;

@FunctionalInterface
public interface TrafficDensityStrategy {
    double calculate(TrafficReading reading);
}
