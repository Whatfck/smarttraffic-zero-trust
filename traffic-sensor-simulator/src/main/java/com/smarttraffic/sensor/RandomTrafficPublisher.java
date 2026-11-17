package com.smarttraffic.sensor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttraffic.domain.TrafficReading;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class RandomTrafficPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    RandomTrafficPublisher(KafkaTemplate<String, String> kafka,
                           @Value("${smarttraffic.kafka.raw-topic:raw_traffic_data}") String topic) {
        this.kafka = kafka;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${smarttraffic.sensor.period-ms:5000}")
    void publish() throws Exception {
        var reading = new TrafficReading("north",
                ThreadLocalRandom.current().nextInt(0, 121),
                ThreadLocalRandom.current().nextDouble(5, 65), Instant.now());
        kafka.send(topic, reading.zoneId(), mapper.writeValueAsString(reading));
    }
}
