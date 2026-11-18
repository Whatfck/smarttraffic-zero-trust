package com.smarttraffic.sensor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttraffic.domain.TrafficReading;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class TrafficSensorSimulatorTest {
    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    @Mock private TrafficReadingGenerator generator;
    @Mock private TrafficReadingPublisher publisher;
    @InjectMocks private TrafficSensorSimulator simulator;

    @Test
    @DisplayName("AAA: cada tick genera una lectura y la publica exactamente una vez")
    void emitPublishesExactlyOnce() {
        // Arrange
        var reading = new TrafficReading("north", 40, 30, T0);
        when(generator.next()).thenReturn(reading);

        // Act
        simulator.emit();

        // Assert
        verify(publisher, times(1)).publish(reading);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    @DisplayName("AAA: el adaptador Kafka usa el zoneId como clave del mensaje")
    @SuppressWarnings("unchecked")
    void kafkaAdapterKeysByZone() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var adapter = new KafkaTrafficReadingPublisher(kafka, mapper, "raw_traffic_data");

        adapter.publish(new TrafficReading("center", 10, 50, T0));

        verify(kafka, times(1)).send(eq("raw_traffic_data"), eq("center"), anyString());
    }

    @Test
    @DisplayName("AAA: el generador recorre las zonas en ronda y produce lecturas validas")
    void generatorRoundRobinProducesValidReadings() {
        var clock = Clock.fixed(T0, ZoneOffset.UTC);
        var generator = new TrafficReadingGenerator(List.of("a", "b"), 180, clock, new Random(42));

        var first = generator.next();
        var second = generator.next();
        var third = generator.next();

        assertEquals("a", first.zoneId());
        assertEquals("b", second.zoneId());
        assertEquals("a", third.zoneId());
        assertTrue(first.vehicles() >= 0 && first.averageSpeed() >= 0);
    }
}
