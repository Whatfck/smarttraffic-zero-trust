package com.smarttraffic.domain;

import java.time.Instant;
import java.util.Objects;

public record TrafficReading(String zoneId, int vehicles, double averageSpeed, Instant observedAt) {
    public TrafficReading {
        if (zoneId == null || zoneId.isBlank()) {
            throw new IllegalArgumentException("zoneId must not be blank");
        }
        if (vehicles < 0 || averageSpeed < 0) {
            throw new IllegalArgumentException("traffic measures must not be negative");
        }
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
    }
}
