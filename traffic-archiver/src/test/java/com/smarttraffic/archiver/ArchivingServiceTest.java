package com.smarttraffic.archiver;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttraffic.archiver.AuditTrail.AuditEventType;
import com.smarttraffic.domain.TrafficReading;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Persistencia aislada con Mockito: sin PostgreSQL ni Kafka reales. */
@ExtendWith(MockitoExtension.class)
class ArchivingServiceTest {

    @Mock private ReadingArchive archive;
    @Mock private AuditTrail audit;
    @InjectMocks private ArchivingService service;

    @Test
    @DisplayName("AAA: al llegar una lectura se persiste exactamente una vez y se audita")
    void archivesExactlyOnceAndAudits() {
        // Arrange
        var reading = new TrafficReading("north", 45, 32.5, Instant.parse("2026-11-17T20:00:00Z"));

        // Act
        service.archive(reading);

        // Assert
        verify(archive, times(1)).store(reading);
        verify(audit, times(1)).record(eq(AuditEventType.READING_ARCHIVED), eq("north"), anyString());
        verifyNoMoreInteractions(archive, audit);
    }

    @Test
    @DisplayName("AAA: el listener convierte el JSON de Kafka y delega en el servicio una sola vez")
    void listenerParsesAndDelegates() {
        var mockService = mock(ArchivingService.class);
        var listener = new KafkaReadingListener(new ObjectMapper().registerModule(new JavaTimeModule()), mockService);

        listener.onReading("""
                {"zoneId":"north","vehicles":45,"averageSpeed":32.5,"observedAt":"2026-11-17T20:00:00Z"}
                """);

        verify(mockService, times(1)).archive(new TrafficReading("north", 45, 32.5,
                Instant.parse("2026-11-17T20:00:00Z")));
        verify(mockService, never()).reject(any(), any());
    }

    @Test
    @DisplayName("AAA: un mensaje que viola las invariantes NO se archiva: se audita como rechazado")
    void invalidReadingIsRejectedNotArchived() {
        var mockService = mock(ArchivingService.class);
        var listener = new KafkaReadingListener(new ObjectMapper().registerModule(new JavaTimeModule()), mockService);

        listener.onReading("{\"zoneId\":\"north\",\"vehicles\":-3,\"averageSpeed\":10,\"observedAt\":\"2026-11-17T20:00:00Z\"}");

        verify(mockService, never()).archive(any());
        verify(mockService, times(1)).reject(anyString(), anyString());
    }
}
