package com.smarttraffic.archiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
final class KafkaArchiver {
    private final ObjectMapper mapper;
    private final TrafficReadingRepository repository;

    KafkaArchiver(TrafficReadingRepository repository) {
        this.mapper = new ObjectMapper(); this.repository = repository;
    }

    @KafkaListener(topics = "${smarttraffic.kafka.raw-topic:raw_traffic_data}",
            groupId = "${smarttraffic.kafka.group-id:traffic-archiver}")
    void archive(String payload) throws Exception {
        var json = mapper.readTree(payload);
        repository.save(new TrafficReadingEntity(json.required("zoneId").asText(),
                json.required("vehicles").asInt(), json.required("averageSpeed").asDouble(),
                parseObservedAt(json)));
    }

    private Instant parseObservedAt(com.fasterxml.jackson.databind.JsonNode json) {
        if (!json.hasNonNull("observedAt")) return Instant.now();
        var observedAt = json.get("observedAt");
        return observedAt.isNumber()
                ? Instant.ofEpochMilli((long) (observedAt.asDouble() * 1000))
                : Instant.parse(observedAt.asText());
    }
}
