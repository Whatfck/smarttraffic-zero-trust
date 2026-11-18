package com.smarttraffic.processor;

import com.smarttraffic.domain.CongestionObserver;
import com.smarttraffic.domain.TrafficDensityStrategy;
import com.smarttraffic.domain.TrafficReading;
import com.smarttraffic.domain.ZoneTrafficAggregate;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Servicio de aplicacion del traffic_density_processor.
 *
 * Todas sus dependencias llegan por CONSTRUCTOR y son interfaces (DIP): la estrategia
 * de densidad, el publicador (puerto hacia RabbitMQ) y los observadores. No conoce
 * Kafka ni RabbitMQ: la topologia de Kafka Streams lo invoca como "aggregator" de la
 * KTable y luego le pide publicar el resultado.
 */
@Service
public class TrafficProcessingService {
    private final TrafficDensityStrategy strategy;
    private final TrafficStatePublisher publisher;
    private final List<CongestionObserver> observers;

    public TrafficProcessingService(TrafficDensityStrategy strategy, TrafficStatePublisher publisher,
                                    List<CongestionObserver> observers) {
        this.strategy = strategy;
        this.publisher = publisher;
        this.observers = List.copyOf(observers);
    }

    /**
     * Aggregator de la KTable: toma el ultimo estado de la zona (o ninguno), rehidrata
     * el agregado de dominio, aplica la lectura y devuelve el nuevo estado.
     */
    public ZoneTrafficSnapshot aggregate(String zoneId, TrafficReading reading, ZoneTrafficSnapshot previous) {
        ZoneTrafficAggregate aggregate = Optional.ofNullable(previous)
                .map(snapshot -> ZoneTrafficAggregate.rehydrate(snapshot, strategy))
                .orElseGet(() -> new ZoneTrafficAggregate(zoneId, strategy));
        observers.forEach(aggregate::subscribe);
        return aggregate.record(reading);
    }

    /** Difunde el estado consolidado de la zona (fanout traffic_updates). */
    public void publish(ZoneTrafficSnapshot snapshot) {
        publisher.publish(snapshot);
    }
}
