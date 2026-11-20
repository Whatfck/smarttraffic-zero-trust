package com.smarttraffic.auth;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas puras en RAM (sin Spring ni red): la Autoridad emite JWT correctos. */
class TokenIssuerTest {
    private static final String SECRET = "sensor-secret-0123456789";
    private KeyPair keyPair;
    private TokenIssuer issuer;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        SigningKeyProvider keys = new SigningKeyProvider() {
            public KeyPair keyPair() { return keyPair; }
            public String keyId() { return "test-kid"; }
        };
        var sensor = new RegisteredClient("traffic-sensor-simulator", SECRET,
                List.of("kafka"), List.of("kafka", "traffic.write"), List.of("SENSOR"));
        ClientRegistry registry = id -> Optional.of(sensor).filter(c -> c.clientId().equals(id));
        // Reloj actual: jjwt valida exp contra la hora real al parsear
        issuer = new TokenIssuer(registry, keys, "http://vm-auth:8081", 300,
                Clock.fixed(Instant.now(), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("AAA: emite un JWT RS256 con sub, aud, scope, roles y kid verificable con la clave publica")
    void issuesSignedTokenWithClaims() {
        // Act
        var token = issuer.issue("traffic-sensor-simulator", SECRET);

        // Assert
        var jws = Jwts.parser().verifyWith(keyPair.getPublic()).build().parseSignedClaims(token.accessToken());
        assertEquals("test-kid", jws.getHeader().getKeyId());
        assertEquals("traffic-sensor-simulator", jws.getPayload().getSubject());
        assertEquals("http://vm-auth:8081", jws.getPayload().getIssuer());
        assertTrue(jws.getPayload().getAudience().contains("kafka"));
        assertEquals("kafka traffic.write", jws.getPayload().get("scope"));
        assertEquals(300, token.expiresIn());
    }

    @Test
    @DisplayName("AAA: rechaza secreto incorrecto y clientes desconocidos (sin token anonimo)")
    void rejectsInvalidCredentials() {
        assertThrows(TokenIssuer.InvalidClientException.class,
                () -> issuer.issue("traffic-sensor-simulator", "wrong-secret-0000000"));
        assertThrows(TokenIssuer.InvalidClientException.class,
                () -> issuer.issue("intruder", SECRET));
        assertThrows(TokenIssuer.InvalidClientException.class,
                () -> issuer.issue(null, null));
    }

    @Test
    @DisplayName("AAA: Fail-Fast del Value Object RegisteredClient ante datos invalidos")
    void registeredClientFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> new RegisteredClient(" ", SECRET, List.of("kafka"), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RegisteredClient("x", "short", List.of("kafka"), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RegisteredClient("x", SECRET, List.of(), List.of(), List.of()));
        assertFalse(new RegisteredClient("x", SECRET, List.of("kafka"), List.of(), List.of()).toString()
                .contains(SECRET), "el secreto nunca debe aparecer en toString/logs");
    }
}
