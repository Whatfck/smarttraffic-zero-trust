package com.smarttraffic.archiver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaArchiverTest {

    @Mock
    private TrafficReadingRepository repository;

    @InjectMocks
    private KafkaArchiver archiver;

    @Test
    @DisplayName("AAA & TDD: Al recibir lectura cruda de Kafka, mapea y persiste exactamente una vez en PostgreSQL (aislado con Mockito)")
    void persistsReadingExactlyOnceUponKafkaEvent() throws Exception {
        // Arrange
        String payload = """
            {
                "zoneId": "north",
                "vehicles": 45,
                "averageSpeed": 32.5,
                "observedAt": "2026-11-17T20:00:00Z"
            }
            """;

        // Act
        archiver.archive(payload);

        // Assert
        verify(repository, times(1)).save(any(TrafficReadingEntity.class));
        verifyNoMoreInteractions(repository);
    }
}
