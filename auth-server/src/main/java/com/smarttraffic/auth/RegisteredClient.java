package com.smarttraffic.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Value Object inmutable: una identidad de servicio (workload identity) conocida por
 * la Autoridad. Cada microservicio tiene su propio client_id, secreto, audiencias
 * (a que recursos puede presentarse: kafka, rabbitmq) y scopes (que puede hacer).
 */
public record RegisteredClient(String clientId, String secret, List<String> audiences,
                               List<String> scopes, List<String> roles) {

    public RegisteredClient {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("secret of " + clientId + " must have at least 16 chars");
        }
        audiences = List.copyOf(Objects.requireNonNull(audiences, "audiences must not be null"));
        scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes must not be null"));
        roles = List.copyOf(Objects.requireNonNull(roles, "roles must not be null"));
        if (audiences.isEmpty()) {
            throw new IllegalArgumentException("client " + clientId + " needs at least one audience");
        }
    }

    /** Comparacion en tiempo constante para no filtrar el secreto por timing. */
    public boolean authenticates(String presentedSecret) {
        if (presentedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                presentedSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** Nunca exponer el secreto en logs. */
    @Override
    public String toString() {
        return "RegisteredClient[" + clientId + ", audiences=" + audiences + ", roles=" + roles + "]";
    }
}
