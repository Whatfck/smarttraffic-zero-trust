package com.smarttraffic.archiver.persistence;

import com.smarttraffic.domain.TrafficReading;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Historico INMUTABLE de lecturas (@Immutable: Hibernate nunca emite UPDATE).
 * Sin setters: solo se crea desde el Value Object de dominio con {@link #from}.
 * El constructor vacio protegido existe unicamente porque JPA lo exige.
 */
@Entity
@Immutable
@Table(name = "traffic_readings", indexes = @Index(name = "ix_readings_zone_time", columnList = "zone_id, observed_at"))
public class TrafficReadingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false, updatable = false, length = 64)
    private String zoneId;

    @Column(nullable = false, updatable = false)
    private int vehicles;

    @Column(name = "average_speed", nullable = false, updatable = false)
    private double averageSpeed;

    @Column(name = "observed_at", nullable = false, updatable = false)
    private Instant observedAt;

    @Column(name = "archived_at", nullable = false, updatable = false)
    private Instant archivedAt;

    protected TrafficReadingEntity() {
    }

    private TrafficReadingEntity(TrafficReading reading, Instant archivedAt) {
        this.zoneId = reading.zoneId();
        this.vehicles = reading.vehicles();
        this.averageSpeed = reading.averageSpeed();
        this.observedAt = reading.observedAt();
        this.archivedAt = archivedAt;
    }

    public static TrafficReadingEntity from(TrafficReading reading, Instant archivedAt) {
        return new TrafficReadingEntity(reading, archivedAt);
    }

    /** Vuelve al modelo de dominio (validado otra vez por el record). */
    public TrafficReading toDomain() {
        return new TrafficReading(zoneId, vehicles, averageSpeed, observedAt);
    }
}
