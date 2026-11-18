package com.smarttraffic.gateway;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitConfiguration {
    // Fanout para difusion de estado de trafico
    @Bean
    FanoutExchange trafficUpdates() {
        return new FanoutExchange("traffic_updates", true, false);
    }

    @Bean
    Queue dashboardUpdates() {
        return new Queue("dashboard_updates", true);
    }

    @Bean
    Binding dashboardBinding(Queue dashboardUpdates, FanoutExchange trafficUpdates) {
        return BindingBuilder.bind(dashboardUpdates).to(trafficUpdates);
    }

    // Colas y Exchange para el ciclo de consultas asincronas seguras (RPC)
    @Bean
    Queue queryTrafficQueue() {
        return new Queue("query_traffic_queue", true);
    }

    @Bean
    TopicExchange queryAnswersExchange() {
        return new TopicExchange("query_answers", true, false);
    }
}
