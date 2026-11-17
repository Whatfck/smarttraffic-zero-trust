package com.smarttraffic.processor;

import com.smarttraffic.domain.TrafficReading;
import com.smarttraffic.domain.VehicleDensityStrategy;
import com.smarttraffic.domain.ZoneTrafficAggregate;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public final class TrafficProcessingService {
    private final Map<String, ZoneTrafficAggregate> aggregates = new ConcurrentHashMap<>();
    private final TrafficStatePublisher publisher;

    public TrafficProcessingService(TrafficStatePublisher publisher) {
        this.publisher = publisher;
    }

    public ZoneTrafficSnapshot process(TrafficReading reading) {
        var aggregate = aggregates.computeIfAbsent(reading.zoneId(),
                id -> new ZoneTrafficAggregate(id, new VehicleDensityStrategy(100)));
        var snapshot = aggregate.record(reading);
        publisher.publish(snapshot);
        return snapshot;
    }
}
