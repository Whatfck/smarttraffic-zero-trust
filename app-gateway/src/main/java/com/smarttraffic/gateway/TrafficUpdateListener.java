package com.smarttraffic.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
final class TrafficUpdateListener {
    private final ObjectMapper mapper;
    private final SimpMessagingTemplate websocket;
    private final TrafficStateReplica replica;
    private final AlertDispatcher alerts;

    TrafficUpdateListener(ObjectMapper mapper, SimpMessagingTemplate websocket,
                          TrafficStateReplica replica, AlertDispatcher alerts) {
        this.mapper = mapper; this.websocket = websocket;
        this.replica = replica; this.alerts = alerts;
    }

    @RabbitListener(queues = "${smarttraffic.rabbit.queue:dashboard_updates}")
    void forward(String payload) throws Exception {
        var snapshot = mapper.readValue(payload, ZoneTrafficSnapshot.class);
        replica.put(snapshot);
        alerts.observe(snapshot);
        websocket.convertAndSend("/topic/traffic", snapshot);
    }
}
