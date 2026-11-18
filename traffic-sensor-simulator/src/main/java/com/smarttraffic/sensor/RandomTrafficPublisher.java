package com.smarttraffic.sensor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttraffic.domain.TrafficReading;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class RandomTrafficPublisher {
    private static final Logger log = LoggerFactory.getLogger(RandomTrafficPublisher.class);
    private static final String[] ZONES = {"north", "center", "south"};

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;
    private final AuthTokenClient authClient;

    RandomTrafficPublisher(KafkaTemplate<String, String> kafka,
                           @Value("${smarttraffic.kafka.raw-topic:raw_traffic_data}") String topic,
                           AuthTokenClient authClient) {
        this.kafka = kafka;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.topic = topic;
        this.authClient = authClient;
    }

    @Scheduled(fixedDelayString = "${smarttraffic.sensor.period-ms:3000}")
    void publish() throws Exception {
        String zoneId = ZONES[ThreadLocalRandom.current().nextInt(ZONES.length)];
        var reading = new TrafficReading(zoneId,
                ThreadLocalRandom.current().nextInt(0, 121),
                ThreadLocalRandom.current().nextDouble(5, 65), Instant.now());

        kafka.send(topic, reading.zoneId(), mapper.writeValueAsString(reading));
        log.info("Sensor reading published for zone='{}' (vehicles={}, speed={} km/h) -> topic '{}'",
                reading.zoneId(), reading.vehicles(), Math.round(reading.averageSpeed()), topic);
    }
}
