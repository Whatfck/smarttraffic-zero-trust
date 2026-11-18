package com.smarttraffic.processor;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

/**
 * Tolerancia a fallos del consumidor stateful:
 *  - si un hilo de Kafka Streams muere (p. ej. un corte de red o un token rechazado),
 *    se REEMPLAZA por uno nuevo en lugar de dejar el procesador detenido;
 *  - si aun asi la aplicacion entra en estado ERROR, el proceso termina para que
 *    Docker lo reinicie (restart: unless-stopped) y la KTable se reconstruya desde
 *    su changelog.
 */
@Configuration
public class StreamsResilienceConfiguration {
    private static final Logger log = LoggerFactory.getLogger(StreamsResilienceConfiguration.class);

    @Bean
    StreamsBuilderFactoryBeanConfigurer streamsResilience() {
        return factoryBean -> {
            factoryBean.setStreamsUncaughtExceptionHandler(exception -> {
                log.error("Stream thread failed, replacing it: {}", exception.toString());
                return StreamThreadExceptionResponse.REPLACE_THREAD;
            });
            factoryBean.setStateListener((newState, oldState) -> {
                log.info("Kafka Streams state {} -> {}", oldState, newState);
                if (newState == KafkaStreams.State.ERROR) {
                    log.error("Kafka Streams entered ERROR state; exiting so Docker restarts the service");
                    new Thread(() -> System.exit(1), "streams-error-exit").start();
                }
            });
        };
    }
}
