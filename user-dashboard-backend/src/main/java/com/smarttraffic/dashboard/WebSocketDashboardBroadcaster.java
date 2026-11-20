package com.smarttraffic.dashboard;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Adaptador: emite por WebSocket (STOMP) en el topic /topic/traffic. */
@Component
public class WebSocketDashboardBroadcaster implements DashboardBroadcaster {
    static final String TOPIC = "/topic/traffic";
    private final SimpMessagingTemplate websocket;

    public WebSocketDashboardBroadcaster(SimpMessagingTemplate websocket) {
        this.websocket = websocket;
    }

    @Override
    public void broadcast(ZoneStatusView view) {
        websocket.convertAndSend(TOPIC, view);
    }
}
