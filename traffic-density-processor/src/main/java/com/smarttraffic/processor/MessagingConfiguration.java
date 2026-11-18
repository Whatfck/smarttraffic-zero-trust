package com.smarttraffic.processor;

import com.smarttraffic.domain.CongestionObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MessagingConfiguration.class);

    /** Exchange Fanout durable: distribucion del estado consolidado a todos los interesados. */
    @Bean
    FanoutExchange trafficUpdatesExchange(@Value("${smarttraffic.rabbit.updates-exchange:traffic_updates}") String name) {
        return new FanoutExchange(name, true, false);
    }

    /** Los records se serializan a JSON (contrato compartido via smarttraffic-domain). */
    @Bean
    MessageConverter jsonMessageConverter(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }

    /** Observer del procesador: deja trazabilidad de cada cambio de nivel. */
    @Bean
    CongestionObserver congestionTransitionLogger() {
        return event -> log.info("Zone '{}' changed {} -> {} (density={})",
                event.zoneId(), event.previous(), event.current(), String.format("%.2f", event.density()));
    }
}
