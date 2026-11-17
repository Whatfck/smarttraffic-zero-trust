package com.smarttraffic.processor;

import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MessagingConfiguration {
    @Bean
    FanoutExchange trafficUpdatesExchange() {
        return new FanoutExchange("traffic_updates", true, false);
    }
}
