package com.smarttraffic.sensor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class RandomTrafficPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private AuthTokenClient authClient;

    @Test
    @DisplayName("AAA & TDD: Publica lecturas simuladas al tópico de Kafka raw_traffic_data")
    void publishesReadingToKafkaTopic() throws Exception {
        // Arrange
        var publisher = new RandomTrafficPublisher(kafkaTemplate, "raw_traffic_data", authClient);

        // Act
        publisher.publish();

        // Assert
        verify(kafkaTemplate, times(1)).send(eq("raw_traffic_data"), anyString(), anyString());
    }
}
