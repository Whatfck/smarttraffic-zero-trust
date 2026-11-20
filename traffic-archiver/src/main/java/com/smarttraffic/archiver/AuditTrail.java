package com.smarttraffic.archiver;

/** Puerto de auditoria (DIP): registro inmutable de lo que paso con cada evento. */
public interface AuditTrail {
    void record(AuditEventType type, String zoneId, String detail);

    enum AuditEventType { READING_ARCHIVED, READING_REJECTED }
}
