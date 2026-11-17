package com.smarttraffic.gateway;

import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class AlertDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    void observe(ZoneTrafficSnapshot snapshot) {
        if (snapshot.state() == CongestionState.CONGESTIONADA) {
            log.warn("CRITICAL congestion alert for zone={} density={}",
                    snapshot.zoneId(), snapshot.density());
        }
    }
}
