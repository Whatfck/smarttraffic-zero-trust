package com.smarttraffic.dashboard;

import java.util.Collection;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Interfaz expuesta al exterior (unico puerto abierto de vm-app: 8082):
 *  - /ws                  endpoint WebSocket STOMP (con SockJS) -> /topic/traffic
 *  - /api/zones           foto actual (solo lectura) para la carga inicial
 *  - /dashboard.html      el frontend estatico
 */
@Configuration
@EnableWebSocketMessageBroker
public class DashboardWebConfiguration implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

    @RestController
    static class ZonesController {
        private final DashboardService service;

        ZonesController(DashboardService service) {
            this.service = service;
        }

        @GetMapping("/api/zones")
        Collection<ZoneStatusView> zones() {
            return service.currentViews();
        }
    }
}
