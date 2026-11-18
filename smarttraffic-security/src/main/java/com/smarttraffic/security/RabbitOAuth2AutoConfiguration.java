package com.smarttraffic.security;

import com.rabbitmq.client.impl.DefaultCredentialsRefreshService;
import com.rabbitmq.client.impl.OAuth2ClientCredentialsGrantCredentialsProvider;
import com.rabbitmq.client.impl.OAuth2ClientCredentialsGrantCredentialsProvider.OAuth2ClientCredentialsGrantCredentialsProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.ConnectionFactoryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Identidad S2S frente a RabbitMQ (plugin rabbitmq_auth_backend_oauth2).
 *
 * En lugar de usuario/contrasena, la conexion AMQP se abre presentando un JWT:
 *  1. Antes de conectar, el cliente pide un token al auth_server (client_credentials).
 *  2. RabbitMQ verifica firma (JWKS de vm-auth), audiencia "rabbitmq" y scopes, y
 *     autoriza cada declare/publish/consume segun esos scopes.
 *  3. El servicio de refresco renueva el token antes de que expire y se lo entrega al
 *     broker sin cortar la conexion (update-secret).
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.rabbitmq.client.ConnectionFactory")
@ConditionalOnProperty(prefix = "smarttraffic.identity", name = "rabbit-oauth2", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ServiceIdentityProperties.class)
public class RabbitOAuth2AutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RabbitOAuth2AutoConfiguration.class);

    @Bean
    ConnectionFactoryCustomizer jwtRabbitConnectionFactoryCustomizer(ServiceIdentityProperties identity) {
        return connectionFactory -> {
            OAuth2ClientCredentialsGrantCredentialsProvider credentials =
                    new OAuth2ClientCredentialsGrantCredentialsProviderBuilder()
                            .tokenEndpointUri(identity.tokenEndpoint())
                            .clientId(identity.clientId())
                            .clientSecret(identity.clientSecret())
                            .grantType("client_credentials")
                            .build();
            connectionFactory.setCredentialsProvider(credentials);
            connectionFactory.setCredentialsRefreshService(
                    new DefaultCredentialsRefreshService.DefaultCredentialsRefreshServiceBuilder().build());
            log.info("RabbitMQ will authenticate with a JWT issued to '{}' by {}",
                    identity.clientId(), identity.tokenEndpoint());
        };
    }
}
