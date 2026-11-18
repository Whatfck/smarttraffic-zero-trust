package com.smarttraffic.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AlertDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    private final TrafficStateReplica replica;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AlertDispatcher(TrafficStateReplica replica, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.replica = replica;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void observe(ZoneTrafficSnapshot snapshot) {
        if (snapshot.state() == CongestionState.CONGESTIONADA) {
            log.warn("CRITICAL congestion alert triggered for zone={} density={}",
                    snapshot.zoneId(), snapshot.density());
        }
    }

    @RabbitListener(queues = "query_traffic_queue")
    public void handleQuery(Message message) {
        try {
            String payload = new String(message.getBody());
            var json = objectMapper.readTree(payload);
            String zoneId = json.has("zoneId") ? json.get("zoneId").asText() : "";

            var snapshot = replica.get(zoneId);
            Map<String, Object> responsePayload;

            if (snapshot != null) {
                responsePayload = Map.of(
                        "zoneId", snapshot.zoneId(),
                        "state", snapshot.state().name(),
                        "density", snapshot.density(),
                        "status", "FOUND"
                );
            } else {
                log.warn("Query received for non-existent zone: '{}'", zoneId);
                responsePayload = Map.of(
                        "zoneId", zoneId,
                        "error", "ZoneNotFoundException: Unknown zone " + zoneId,
                        "status", "NOT_FOUND"
                );
            }

            String replyTo = message.getMessageProperties().getReplyTo();
            String correlationId = message.getMessageProperties().getCorrelationId();

            if (replyTo != null && !replyTo.isBlank()) {
                MessageProperties props = new MessageProperties();
                if (correlationId != null) {
                    props.setCorrelationId(correlationId);
                }
                props.setContentType("application/json");

                byte[] body = objectMapper.writeValueAsBytes(responsePayload);
                Message replyMessage = new Message(body, props);

                rabbitTemplate.send("query_answers", replyTo, replyMessage);
                log.info("Answered async query for zone '{}' to exchange 'query_answers' with routingKey '{}'",
                        zoneId, replyTo);
            }
        } catch (Exception e) {
            log.error("Failed to process async query: {}", e.getMessage(), e);
        }
    }
}
