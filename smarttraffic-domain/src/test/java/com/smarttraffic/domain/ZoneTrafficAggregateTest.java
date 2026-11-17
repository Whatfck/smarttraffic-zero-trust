package com.smarttraffic.domain;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ZoneTrafficAggregateTest {
    private static TrafficReading reading(int vehicles, double speed) {
        return new TrafficReading("north", vehicles, speed, Instant.EPOCH);
    }

    @Test
    void appliesStrategyAndStateTransitions() {
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));

        assertEquals(CongestionState.DESPEJADA, aggregate.record(reading(20, 40)).state());
        assertEquals(CongestionState.MODERADA, aggregate.record(reading(50, 40)).state());
        assertEquals(CongestionState.CONGESTIONADA, aggregate.record(reading(80, 40)).state());
    }

    @Test
    void notifiesOnlyWhenEnteringCriticalState() {
        var observer = mock(CongestionObserver.class);
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));
        aggregate.addObserver(observer);

        aggregate.record(reading(80, 40));
        aggregate.record(reading(90, 40));

        verify(observer, times(1)).onCriticalChange("north", CongestionState.DESPEJADA,
                CongestionState.CONGESTIONADA, 0.8);
    }

    @Test
    void rejectsInvalidReadingsAndForeignZones() {
        assertThrows(IllegalArgumentException.class, () -> reading(-1, 10));
        var aggregate = new ZoneTrafficAggregate("north", new VehicleDensityStrategy(100));
        assertThrows(IllegalArgumentException.class,
                () -> aggregate.record(new TrafficReading("south", 1, 10, Instant.EPOCH)));
    }
}
