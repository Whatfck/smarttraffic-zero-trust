package com.smarttraffic.sensor;

import com.smarttraffic.domain.TrafficReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Envio periodico de lecturas (borde de la red, vm-ingest). Dependencias por constructor. */
@Component
public class TrafficSensorSimulator {
    private static final Logger log = LoggerFactory.getLogger(TrafficSensorSimulator.class);

    private final TrafficReadingGenerator generator;
    private final TrafficReadingPublisher publisher;

    public TrafficSensorSimulator(TrafficReadingGenerator generator, TrafficReadingPublisher publisher) {
        this.generator = generator;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${smarttraffic.sensor.period-ms:1000}", initialDelayString = "${smarttraffic.sensor.initial-delay-ms:5000}")
    public void emit() {
        TrafficReading reading = generator.next();
        publisher.publish(reading);
        log.info("Reading published zone={} vehicles={} speed={}km/h",
                reading.zoneId(), reading.vehicles(), Math.round(reading.averageSpeed()));
    }
}
