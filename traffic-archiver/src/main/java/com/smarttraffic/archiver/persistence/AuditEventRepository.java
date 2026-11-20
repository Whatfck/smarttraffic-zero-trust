package com.smarttraffic.archiver.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data (solo lo usan los adaptadores JPA). */
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
}
