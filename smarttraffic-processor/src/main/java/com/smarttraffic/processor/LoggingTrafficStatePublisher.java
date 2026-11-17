package com.smarttraffic.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class LoggingTrafficStatePublisher implements TrafficStatePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;

    LoggingTrafficStatePublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                                 @Value("${smarttraffic.rabbit.exchange:traffic_updates}") String exchange) {
        this.rabbitTemplate = rabbitTemplate; this.objectMapper = objectMapper;
        this.exchange = exchange;
    }

    @Override
    public void publish(ZoneTrafficSnapshot snapshot) {
        try {
            rabbitTemplate.convertAndSend(exchange, "", objectMapper.writeValueAsString(snapshot));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize traffic state", exception);
        }
    }
}
