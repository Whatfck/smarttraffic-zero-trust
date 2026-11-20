package com.smarttraffic.archiver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.TrafficReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada: consume raw_traffic_data (autenticado con su JWT via
 * SASL/OAUTHBEARER) y convierte el JSON en el Value Object TrafficReading. Si el
 * mensaje viola las invariantes (fail-fast del record) se audita como rechazado.
 */
@Component
public class KafkaReadingListener {
    private static final Logger log = LoggerFactory.getLogger(KafkaReadingListener.class);

    private final ObjectMapper mapper;
    private final ArchivingService service;

    public KafkaReadingListener(ObjectMapper mapper, ArchivingService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @KafkaListener(topics = "${smarttraffic.kafka.raw-topic:raw_traffic_data}",
            groupId = "${smarttraffic.kafka.group-id:traffic-archiver}")
    public void onReading(String payload) {
        TrafficReading reading;
        try {
            reading = mapper.readValue(payload, TrafficReading.class);
        } catch (Exception invalid) {
            log.warn("Rejected invalid reading: {}", invalid.getMessage());
            service.reject(payload, invalid.getClass().getSimpleName());
            return;
        }
        service.archive(reading);
        log.debug("Archived reading of zone {}", reading.zoneId());
    }
}
