package com.smarttraffic.archiver.persistence;

import com.smarttraffic.archiver.AuditTrail;
import com.smarttraffic.archiver.ReadingArchive;
import com.smarttraffic.domain.TrafficReading;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptadores JPA de los puertos ReadingArchive y AuditTrail. Son los UNICOS que
 * conocen Spring Data / Hibernate; el resto del servicio solo ve las interfaces.
 */
final class JpaPersistenceAdapters {
    private JpaPersistenceAdapters() {
    }

    @Component
    static class JpaReadingArchive implements ReadingArchive {
        private final TrafficReadingRepository repository;
        private final Clock clock;

        JpaReadingArchive(TrafficReadingRepository repository, Clock clock) {
            this.repository = repository;
            this.clock = clock;
        }

        @Override
        public void store(TrafficReading reading) {
            repository.save(TrafficReadingEntity.from(reading, clock.instant()));
        }
    }

    @Component
    static class JpaAuditTrail implements AuditTrail {
        private final AuditEventRepository repository;
        private final Clock clock;
        private final String actor;

        JpaAuditTrail(AuditEventRepository repository, Clock clock,
                      @Value("${spring.application.name}") String actor) {
            this.repository = repository;
            this.clock = clock;
            this.actor = actor;
        }

        @Override
        public void record(AuditEventType type, String zoneId, String detail) {
            repository.save(AuditEventEntity.of(type, zoneId, detail, clock.instant(), actor));
        }
    }
}
