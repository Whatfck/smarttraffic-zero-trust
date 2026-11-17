package com.smarttraffic.archiver;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "traffic_readings")
public class TrafficReadingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String zoneId;
    @Column(nullable = false)
    private int vehicles;
    @Column(nullable = false)
    private double averageSpeed;
    @Column(nullable = false)
    private Instant observedAt;
    protected TrafficReadingEntity() {}
    TrafficReadingEntity(String zoneId, int vehicles, double averageSpeed, Instant observedAt) {
        this.zoneId = zoneId; this.vehicles = vehicles; this.averageSpeed = averageSpeed; this.observedAt = observedAt;
    }
}
