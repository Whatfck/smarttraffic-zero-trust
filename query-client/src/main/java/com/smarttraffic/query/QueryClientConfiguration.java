package com.smarttraffic.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.RouteEvaluation;
import com.smarttraffic.domain.RouteQuery;
import java.net.InetAddress;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Topologia y adaptadores del query_client:
 *  - publica RouteQuery en la cola query_traffic_queue (exchange por defecto);
 *  - recibe RouteEvaluation en SU cola exclusiva query_client.<id>, enlazada al
 *    Topic Exchange query_answers con la routing key query.<id>.
 * Con varias replicas, cada una solo recibe las respuestas a sus propias consultas.
 */
@Configuration
public class QueryClientConfiguration {

    @Bean
    ClientIdentity clientIdentity() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception unresolvable) {
            host = "vm-app";
        }
        return new ClientIdentity("qc-" + host + "-" + UUID.randomUUID().toString().substring(0, 4));
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
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
    Queue replyQueue(ClientIdentity identity) {
        return QueueBuilder.nonDurable(identity.replyQueue()).exclusive().autoDelete().build();
    }

    @Bean
    Binding replyBinding(Queue replyQueue, TopicExchange queryAnswersExchange, ClientIdentity identity) {
        return BindingBuilder.bind(replyQueue).to(queryAnswersExchange).with(identity.routingKey());
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    RouteQuerySender routeQuerySender(AmqpTemplate amqp, @Value("${smarttraffic.rabbit.query-queue}") String queue) {
        return query -> amqp.convertAndSend("", queue, query);
    }

    /** Trayectos aleatorios de 2-3 zonas; de vez en cuando incluye una zona inexistente. */
    @Bean
    RouteQueryClient routeQueryClient(ClientIdentity identity, RouteQuerySender sender, Clock clock,
                                      @Value("${smarttraffic.query.zones}") List<String> zones) {
        return new RouteQueryClient(identity, sender, () -> {
            List<String> shuffled = new ArrayList<>(zones);
            Collections.shuffle(shuffled);
            List<String> route = new ArrayList<>(shuffled.subList(0, 2 + ThreadLocalRandom.current().nextInt(2)));
            if (ThreadLocalRandom.current().nextInt(5) == 0) {
                route.add("atlantis");
            }
            return route;
        }, clock);
    }

    @Component
    static class QueryClientDriver {
        private final RouteQueryClient client;

        QueryClientDriver(RouteQueryClient client) {
            this.client = client;
        }

        @Scheduled(fixedDelayString = "${smarttraffic.query.period-ms:7000}", initialDelayString = "${smarttraffic.query.initial-delay-ms:20000}")
        void tick() {
            client.ask();
        }

        @RabbitListener(queues = "#{replyQueue.name}")
        void onAnswer(RouteEvaluation evaluation) {
            client.onAnswer(evaluation);
        }
    }
}
