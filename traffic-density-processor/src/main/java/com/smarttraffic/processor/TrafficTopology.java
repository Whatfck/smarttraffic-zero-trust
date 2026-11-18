package com.smarttraffic.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.TrafficReading;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonSerde;

/**
 * Topologia Kafka Streams = consumidor STATEFUL.
 *
 *   raw_traffic_data (KStream, clave = zoneId)
 *        -> groupByKey
 *        -> aggregate(...)  => KTable<zoneId, ZoneTrafficSnapshot>   (estado por zona)
 *        -> toStream().foreach(publish)  => RabbitMQ fanout traffic_updates
 *
 * La KTable vive en un state store respaldado por un topic "changelog" en Kafka: si
 * el contenedor muere, al volver reconstruye el estado de cada zona. Con varias
 * replicas (deploy.replicas) Kafka reparte las particiones y cada replica es duena
 * del estado de sus zonas.
 */
@Configuration
public class TrafficTopology {
    public static final String STATE_STORE = "zone-traffic-state";

    private final String rawTopic;

    public TrafficTopology(@Value("${smarttraffic.kafka.raw-topic:raw_traffic_data}") String rawTopic) {
        this.rawTopic = rawTopic;
    }

    @Bean
    NewTopic rawTrafficTopic() {
        return TopicBuilder.name(rawTopic).partitions(3).replicas(1).build();
    }

    @Bean
    KTable<String, ZoneTrafficSnapshot> zoneTrafficTable(StreamsBuilder builder, ObjectMapper mapper,
                                                         TrafficProcessingService service) {
        var readingSerde = new JsonSerde<>(TrafficReading.class, mapper).ignoreTypeHeaders().noTypeInfo();
        var snapshotSerde = new JsonSerde<>(ZoneTrafficSnapshot.class, mapper).ignoreTypeHeaders().noTypeInfo();

        KTable<String, ZoneTrafficSnapshot> table = builder
                .stream(rawTopic, Consumed.with(Serdes.String(), readingSerde))
                .filter((zoneId, reading) -> reading != null)
                .groupByKey(Grouped.with(Serdes.String(), readingSerde))
                .aggregate(() -> null,
                        service::aggregate,
                        Materialized.<String, ZoneTrafficSnapshot>as(Stores.inMemoryKeyValueStore(STATE_STORE))
                                .withKeySerde(Serdes.String())
                                .withValueSerde(snapshotSerde));

        table.toStream().foreach((zoneId, snapshot) -> service.publish(snapshot));
        return table;
    }
}
