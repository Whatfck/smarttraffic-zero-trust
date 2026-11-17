package com.smarttraffic.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.TrafficReading;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
final class KafkaTrafficListener {
    private final ObjectMapper objectMapper;
    private final TrafficProcessingService service;

    KafkaTrafficListener(ObjectMapper objectMapper, TrafficProcessingService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @KafkaListener(topics = "${smarttraffic.kafka.raw-topic:raw_traffic_data}",
            groupId = "${smarttraffic.kafka.group-id:traffic-density-processor}")
    void consume(String payload) throws Exception {
        var message = objectMapper.readTree(payload);
        service.process(new TrafficReading(
                message.required("zoneId").asText(),
                message.required("vehicles").asInt(),
                message.required("averageSpeed").asDouble(),
                parseObservedAt(message)));
    }

    private Instant parseObservedAt(com.fasterxml.jackson.databind.JsonNode message) {
        if (!message.hasNonNull("observedAt")) return Instant.now();
        var observedAt = message.get("observedAt");
        return observedAt.isNumber()
                ? Instant.ofEpochMilli((long) (observedAt.asDouble() * 1000))
                : Instant.parse(observedAt.asText());
    }
}
