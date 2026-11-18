package com.smarttraffic.archiver;

import com.smarttraffic.archiver.AuditTrail.AuditEventType;
import com.smarttraffic.domain.TrafficReading;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: archivar de forma duradera una lectura cruda y dejar su auditoria.
 * Ambas escrituras ocurren en la MISMA transaccion: o quedan las dos o ninguna.
 * Solo conoce puertos (interfaces) inyectados por constructor.
 */
@Service
public class ArchivingService {
    private final ReadingArchive archive;
    private final AuditTrail audit;

    public ArchivingService(ReadingArchive archive, AuditTrail audit) {
        this.archive = archive;
        this.audit = audit;
    }

    @Transactional
    public void archive(TrafficReading reading) {
        archive.store(reading);
        audit.record(AuditEventType.READING_ARCHIVED, reading.zoneId(),
                "vehicles=" + reading.vehicles() + " speed=" + reading.averageSpeed());
    }

    @Transactional
    public void reject(String rawPayload, String reason) {
        String excerpt = rawPayload == null ? "" : rawPayload.substring(0, Math.min(200, rawPayload.length()));
        audit.record(AuditEventType.READING_REJECTED, null, reason + " | payload=" + excerpt);
    }
}
