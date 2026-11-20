package com.smarttraffic.processor;

import com.smarttraffic.domain.ZoneTrafficSnapshot;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Adaptador del puerto TrafficStatePublisher: publica en el Exchange Fanout de RabbitMQ. */
@Component
public class RabbitTrafficStatePublisher implements TrafficStatePublisher {
    private final AmqpTemplate amqp;
    private final String exchange;

    public RabbitTrafficStatePublisher(AmqpTemplate amqp,
                                       @Value("${smarttraffic.rabbit.updates-exchange:traffic_updates}") String exchange) {
        this.amqp = amqp;
        this.exchange = exchange;
    }

    @Override
    public void publish(ZoneTrafficSnapshot snapshot) {
        // Fanout ignora la routing key: todas las colas enlazadas reciben una copia.
        amqp.convertAndSend(exchange, "", snapshot);
    }
}
