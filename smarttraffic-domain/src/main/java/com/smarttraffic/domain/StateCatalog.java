package com.smarttraffic.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Mapa nivel -> estado, para rehidratar el patron State sin usar switch. */
final class StateCatalog {
    private static final Map<CongestionState, Supplier<TrafficZoneState>> STATES = new EnumMap<>(Map.of(
            CongestionState.DESPEJADA, ClearZoneState::new,
            CongestionState.MODERADA, ModerateZoneState::new,
            CongestionState.CONGESTIONADA, CongestedZoneState::new));

    private StateCatalog() {}

    static TrafficZoneState forLevel(CongestionState level) {
        return STATES.get(Objects.requireNonNull(level, "level must not be null")).get();
    }
}
