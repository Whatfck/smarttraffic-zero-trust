package com.smarttraffic.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "smarttraffic.query-client.enabled", havingValue = "true", matchIfMissing = true)
public class QueryClientSimulator {
    private static final Logger log = LoggerFactory.getLogger(QueryClientSimulator.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final TopicExchange queryAnswersExchange;
    private final ObjectMapper objectMapper;
    private final String clientId = "client-" + UUID.randomUUID().toString().substring(0, 8);
    private String clientQueueName;

    public QueryClientSimulator(
            RabbitTemplate rabbitTemplate,
            TopicExchange queryAnswersExchange,
            ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.queryAnswersExchange = queryAnswersExchange;
        this.objectMapper = objectMapper;
        this.rabbitAdmin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
    }

    @PostConstruct
    public void setupClientQueue() {
        // Cola exclusiva y temporal de respuesta para este query_client
        clientQueueName = "query_client_reply_" + clientId;
        Queue replyQueue = new Queue(clientQueueName, false, true, true);
        rabbitAdmin.declareQueue(replyQueue);
        rabbitAdmin.declareBinding(BindingBuilder.bind(replyQueue).to(queryAnswersExchange).with(clientQueueName));
        log.info("Initialized QueryClient instance '{}' with exclusive reply queue '{}'", clientId, clientQueueName);
    }

    @Scheduled(fixedDelayString = "${smarttraffic.query-client.interval-ms:15000}", initialDelay = 10000)
    public void sendSampleQuery() {
        try {
            String[] targetZones = {"north", "ZONE-CENTRO", "unknown-zone"};
            String zoneToQuery = targetZones[(int) (Math.random() * targetZones.length)];
            String correlationId = UUID.randomUUID().toString();

            MessageProperties props = new MessageProperties();
            props.setReplyTo(clientQueueName);
            props.setCorrelationId(correlationId);
            props.setContentType("application/json");

            byte[] body = objectMapper.writeValueAsBytes(Map.of("zoneId", zoneToQuery));
            Message requestMessage = new Message(body, props);

            rabbitTemplate.send("", "query_traffic_queue", requestMessage);
            log.info("QueryClient [{}] -> Sent async RPC query for zone '{}' (correlationId={})",
                    clientId, zoneToQuery, correlationId);

            // Intentar recibir respuesta no bloqueante breve
            Message response = rabbitTemplate.receive(clientQueueName, 2000);
            if (response != null && correlationId.equals(response.getMessageProperties().getCorrelationId())) {
                log.info("QueryClient [{}] <- Received RPC reply: {}", clientId, new String(response.getBody()));
            }
        } catch (Exception e) {
            log.warn("QueryClient simulation exception: {}", e.getMessage());
        }
    }
}
