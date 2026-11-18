package com.smarttraffic.sensor;

import com.smarttraffic.domain.TrafficReading;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Genera lecturas plausibles: cada zona sigue una "ola" de trafico (hora pico
 * simulada, desfasada por zona) mas ruido. Asi en la demo se ven zonas pasar por
 * DESPEJADA, MODERADA y CONGESTIONADA. Cada lectura es un Value Object validado.
 */
@Component
public class TrafficReadingGenerator {
    private final List<String> zones;
    private final double waveSeconds;
    private final Clock clock;
    private final Random random;
    private int cursor;

    public TrafficReadingGenerator(@Value("${smarttraffic.sensor.zones}") List<String> zones,
                                   @Value("${smarttraffic.sensor.wave-seconds:180}") double waveSeconds,
                                   Clock clock) {
        this(zones, waveSeconds, clock, new Random());
    }

    TrafficReadingGenerator(List<String> zones, double waveSeconds, Clock clock, Random random) {
        if (zones == null || zones.isEmpty()) {
            throw new IllegalArgumentException("at least one zone is required");
        }
        this.zones = List.copyOf(zones);
        this.waveSeconds = waveSeconds;
        this.clock = clock;
        this.random = random;
    }

    /** Recorre las zonas en ronda (round-robin) y devuelve la siguiente lectura. */
    public synchronized TrafficReading next() {
        int index = cursor;
        cursor = (cursor + 1) % zones.size();
        Instant now = clock.instant();
        double phase = (now.getEpochSecond() / waveSeconds) * 2 * Math.PI + index * (2 * Math.PI / zones.size());
        double load = (Math.sin(phase) + 1) / 2;                  // 0..1
        int vehicles = (int) Math.max(0, Math.round(10 + load * 95 + random.nextGaussian() * 6));
        double speed = Math.max(0, 65 - load * 55 + random.nextGaussian() * 4);
        return new TrafficReading(zones.get(index), vehicles, speed, now);
    }
}
