package com.smarttraffic.gateway;

import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
final class TrafficStateReplica {
    private final Map<String, ZoneTrafficSnapshot> state = new ConcurrentHashMap<>();

    void put(ZoneTrafficSnapshot snapshot) {
        state.put(snapshot.zoneId(), snapshot);
    }

    ZoneTrafficSnapshot get(String zoneId) {
        return state.get(zoneId);
    }
}
