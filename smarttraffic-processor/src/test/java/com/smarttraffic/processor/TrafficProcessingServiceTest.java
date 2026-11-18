package com.smarttraffic.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.TrafficReading;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrafficProcessingServiceTest {

    @Mock
    private TrafficStatePublisher publisher;

    @InjectMocks
    private TrafficProcessingService service;

    @Test
    @DisplayName("AAA & TDD: Al procesar lectura, calcula densidad, actualiza estado y despacha a RabbitMQ exactamente una vez")
    void updatesAggregateAndPublishesToRabbitMQ() {
        // Arrange
        var reading = new TrafficReading("north", 85, 20.0, Instant.now());

        // Act
        ZoneTrafficSnapshot snapshot = service.process(reading);

        // Assert
        assertNotNull(snapshot);
        assertEquals("north", snapshot.zoneId());
        assertEquals(CongestionState.CONGESTIONADA, snapshot.state());
        verify(publisher, times(1)).publish(any(ZoneTrafficSnapshot.class));
        verifyNoMoreInteractions(publisher);
    }
}
