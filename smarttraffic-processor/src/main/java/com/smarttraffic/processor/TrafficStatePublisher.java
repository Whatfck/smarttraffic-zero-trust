package com.smarttraffic.processor;

import com.smarttraffic.domain.ZoneTrafficSnapshot;

public interface TrafficStatePublisher {
    void publish(ZoneTrafficSnapshot snapshot);
}
