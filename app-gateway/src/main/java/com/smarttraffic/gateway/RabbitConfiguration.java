package com.smarttraffic.gateway;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RabbitConfiguration {
    @Bean FanoutExchange trafficUpdates() { return new FanoutExchange("traffic_updates", true, false); }
    @Bean Queue dashboardUpdates() { return new Queue("dashboard_updates", true); }
    @Bean Binding dashboardBinding(Queue dashboardUpdates, FanoutExchange trafficUpdates) {
        return BindingBuilder.bind(dashboardUpdates).to(trafficUpdates);
    }
}
