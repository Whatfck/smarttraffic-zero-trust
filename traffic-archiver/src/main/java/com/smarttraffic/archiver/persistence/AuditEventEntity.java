package com.smarttraffic.archiver.persistence;

import com.smarttraffic.archiver.AuditTrail.AuditEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Registro de auditoria inmutable (append-only). */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private AuditEventType type;

    @Column(name = "zone_id", updatable = false, length = 64)
    private String zoneId;

    @Column(nullable = false, updatable = false, length = 512)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, updatable = false, length = 64)
    private String actor;

    protected AuditEventEntity() {
    }

    private AuditEventEntity(AuditEventType type, String zoneId, String detail, Instant occurredAt, String actor) {
        this.type = type;
        this.zoneId = zoneId;
        this.detail = detail.length() > 512 ? detail.substring(0, 512) : detail;
        this.occurredAt = occurredAt;
        this.actor = actor;
    }

    public static AuditEventEntity of(AuditEventType type, String zoneId, String detail, Instant at, String actor) {
        return new AuditEventEntity(type, zoneId, detail, at, actor);
    }
}
