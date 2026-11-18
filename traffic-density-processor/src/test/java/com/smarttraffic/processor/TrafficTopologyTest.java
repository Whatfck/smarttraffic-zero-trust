package com.smarttraffic.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smarttraffic.domain.CongestionState;
import com.smarttraffic.domain.TrafficReading;
import com.smarttraffic.domain.VehicleDensityStrategy;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Prueba de la topologia stateful con TopologyTestDriver: corre la KTable en memoria,
 * sin broker Kafka ni sockets.
 */
class TrafficTopologyTest {

    @Test
    @DisplayName("AAA: la KTable acumula el estado por zona y publica cada actualizacion")
    void ktableKeepsStatePerZoneAndPublishes() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        TrafficStatePublisher publisher = mock(TrafficStatePublisher.class);
        var service = new TrafficProcessingService(new VehicleDensityStrategy(100), publisher, List.of());
        StreamsBuilder builder = new StreamsBuilder();
        new TrafficTopology("raw_traffic_data").zoneTrafficTable(builder, mapper, service);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                org.apache.kafka.streams.errors.LogAndContinueExceptionHandler.class);

        try (var driver = new TopologyTestDriver(builder.build(), props)) {
            var input = driver.createInputTopic("raw_traffic_data", new StringSerializer(), new StringSerializer());
            Instant t = Instant.parse("2026-11-18T10:00:00Z");

            // Act
            input.pipeInput("north", mapper.writeValueAsString(new TrafficReading("north", 20, 50, t)));
            input.pipeInput("north", mapper.writeValueAsString(new TrafficReading("north", 90, 5, t.plusSeconds(1))));
            input.pipeInput("south", mapper.writeValueAsString(new TrafficReading("south", 50, 30, t)));
            input.pipeInput("north", "{\"zoneId\":\"north\",\"vehicles\":-5}"); // invalido: se descarta

            // Assert
            KeyValueStore<String, ZoneTrafficSnapshot> store = driver.getKeyValueStore(TrafficTopology.STATE_STORE);
            assertEquals(CongestionState.CONGESTIONADA, store.get("north").state());
            assertEquals(2, store.get("north").readings());
            assertEquals(CongestionState.MODERADA, store.get("south").state());

            var captor = ArgumentCaptor.forClass(ZoneTrafficSnapshot.class);
            verify(publisher, times(3)).publish(captor.capture());
            assertEquals("south", captor.getAllValues().get(2).zoneId());
        }
    }
}
