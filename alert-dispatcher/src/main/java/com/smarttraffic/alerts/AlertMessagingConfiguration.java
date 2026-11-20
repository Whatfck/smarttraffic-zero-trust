package com.smarttraffic.alerts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.RouteEvaluation;
import com.smarttraffic.domain.RouteQuery;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.util.UUID;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Topologia RabbitMQ del alert_dispatcher:
 *  - cola exclusiva alert_dispatcher.<id> enlazada al Fanout traffic_updates (replica);
 *  - cola durable query_traffic_queue de donde consume las consultas;
 *  - Topic Exchange query_answers donde publica la respuesta con la routing key
 *    "query.<clientId>", que solo coincide con la cola de ese cliente.
 */
@Configuration
public class AlertMessagingConfiguration {
    public static String routingKeyFor(String clientId) {
        return "query." + clientId;
    }

    @Bean
    FanoutExchange trafficUpdatesExchange(@Value("${smarttraffic.rabbit.updates-exchange}") String name) {
        return new FanoutExchange(name, true, false);
    }

    @Bean
    Queue alertReplicaQueue() {
        return QueueBuilder.nonDurable("alert_dispatcher." + UUID.randomUUID().toString().substring(0, 8))
                .exclusive().autoDelete().build();
    }

    @Bean
    Binding alertReplicaBinding(Queue alertReplicaQueue, FanoutExchange trafficUpdatesExchange) {
        return BindingBuilder.bind(alertReplicaQueue).to(trafficUpdatesExchange);
    }

    @Bean
    Queue queryTrafficQueue(@Value("${smarttraffic.rabbit.query-queue}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    TopicExchange queryAnswersExchange(@Value("${smarttraffic.rabbit.answers-exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }

    /** Adaptador de salida: publica en el Topic Exchange con la clave del cliente. */
    @Component
    static class RabbitRouteAnswerPublisher implements RouteAnswerPublisher {
        private final AmqpTemplate amqp;
        private final String exchange;

        RabbitRouteAnswerPublisher(AmqpTemplate amqp, @Value("${smarttraffic.rabbit.answers-exchange}") String exchange) {
            this.amqp = amqp;
            this.exchange = exchange;
        }

        @Override
        public void answer(String clientId, RouteEvaluation evaluation) {
            amqp.convertAndSend(exchange, routingKeyFor(clientId), evaluation);
        }
    }

    /** Adaptadores de entrada AMQP -> servicio. */
    @Component
    static class AlertListeners {
        private final RouteQueryService service;

        AlertListeners(RouteQueryService service) {
            this.service = service;
        }

        @RabbitListener(queues = "#{alertReplicaQueue.name}")
        void onTrafficUpdate(ZoneTrafficSnapshot snapshot) {
            service.onTrafficUpdate(snapshot);
        }

        @RabbitListener(queues = "${smarttraffic.rabbit.query-queue}", concurrency = "2")
        void onRouteQuery(RouteQuery query) {
            service.answer(query);
        }
    }
}
