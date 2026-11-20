package com.smarttraffic.sensor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.TrafficReading;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador Kafka del puerto TrafficReadingPublisher. La CLAVE del mensaje es el
 * zoneId: asi todas las lecturas de una zona caen en la misma particion, en orden, y
 * la KTable del procesador puede agrupar por clave sin reparticionar.
 */
@Component
public class KafkaTrafficReadingPublisher implements TrafficReadingPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public KafkaTrafficReadingPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper,
                                        @Value("${smarttraffic.kafka.raw-topic:raw_traffic_data}") String topic) {
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }

    @Override
    public void publish(TrafficReading reading) {
        try {
            kafka.send(topic, reading.zoneId(), mapper.writeValueAsString(reading));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize reading of zone " + reading.zoneId(), e);
        }
    }
}
