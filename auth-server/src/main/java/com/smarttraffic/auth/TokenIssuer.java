package com.smarttraffic.auth;

import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servicio de la Autoridad: valida credenciales de cliente y emite un JWT RS256.
 *
 * Claims emitidos:
 *  - iss: la Autoridad (vm-auth)            - sub: client_id del microservicio
 *  - aud: recursos permitidos (kafka, rabbitmq)
 *  - scope: permisos. Kafka exige "kafka"; RabbitMQ interpreta los scopes con
 *    formato rabbitmq.<configure|read|write>:<vhost>/<recurso>
 *  - roles: rol logico del servicio         - exp/iat/jti
 */
@Service
public final class TokenIssuer {
    private final ClientRegistry registry;
    private final SigningKeyProvider keys;
    private final String issuer;
    private final long ttlSeconds;
    private final Clock clock;

    public TokenIssuer(ClientRegistry registry, SigningKeyProvider keys,
                       @Value("${smarttraffic.auth.issuer}") String issuer,
                       @Value("${smarttraffic.auth.token-ttl-seconds:300}") long ttlSeconds,
                       Clock clock) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("token ttl must be positive");
        }
        this.registry = registry;
        this.keys = keys;
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    public IssuedToken issue(String clientId, String clientSecret) {
        RegisteredClient client = registry.find(clientId)
                .filter(candidate -> candidate.authenticates(clientSecret))
                .orElseThrow(() -> new InvalidClientException(clientId));

        Instant now = clock.instant();
        String jwt = Jwts.builder()
                .header().keyId(keys.keyId()).and()
                .issuer(issuer)
                .subject(client.clientId())
                .audience().add(client.audiences()).and()
                .claim("scope", String.join(" ", client.scopes()))
                .claim("roles", client.roles())
                .claim("client_id", client.clientId())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(keys.keyPair().getPrivate(), Jwts.SIG.RS256)
                .compact();
        return new IssuedToken(jwt, ttlSeconds, String.join(" ", client.scopes()));
    }

    /** Respuesta inmutable con el formato estandar OAuth2 (RFC 6749 seccion 5.1). */
    public record IssuedToken(String accessToken, long expiresIn, String scope) {
        public IssuedToken {
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalArgumentException("accessToken must not be blank");
            }
            if (expiresIn <= 0) {
                throw new IllegalArgumentException("expiresIn must be positive");
            }
        }
    }

    /** Excepcion de dominio: credenciales de cliente invalidas o cliente desconocido. */
    public static final class InvalidClientException extends RuntimeException {
        public InvalidClientException(String clientId) {
            super("invalid client credentials for '" + clientId + "'");
        }
    }
}
