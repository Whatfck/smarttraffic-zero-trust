package com.smarttraffic.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttraffic.domain.ZoneTrafficSnapshot;
import java.util.UUID;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Suscripcion al Fanout traffic_updates. Cada INSTANCIA declara su PROPIA cola
 * exclusiva (dashboard.<id>): asi todas las replicas reciben todas las
 * actualizaciones (difusion), en vez de repartirselas como consumidores competidores.
 */
@Configuration
public class DashboardMessagingConfiguration {

    @Bean
    FanoutExchange trafficUpdatesExchange(@Value("${smarttraffic.rabbit.updates-exchange:traffic_updates}") String name) {
        return new FanoutExchange(name, true, false);
    }

    @Bean
    Queue dashboardQueue() {
        return QueueBuilder.nonDurable("dashboard." + UUID.randomUUID().toString().substring(0, 8))
                .exclusive().autoDelete().build();
    }

    @Bean
    Binding dashboardBinding(Queue dashboardQueue, FanoutExchange trafficUpdatesExchange) {
        return BindingBuilder.bind(dashboardQueue).to(trafficUpdatesExchange);
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }

    /** Adaptador de entrada AMQP -> servicio. */
    @Component
    static class TrafficUpdatesListener {
        private final DashboardService service;

        TrafficUpdatesListener(DashboardService service) {
            this.service = service;
        }

        @RabbitListener(queues = "#{dashboardQueue.name}")
        void onUpdate(ZoneTrafficSnapshot snapshot) {
            service.onTrafficUpdate(snapshot);
        }
    }
}
