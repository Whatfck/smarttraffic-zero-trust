package com.smarttraffic.query;

/**
 * Identidad de ESTA instancia de query_client. Cada replica tiene un id distinto
 * (prefijo + hostname del contenedor), y de el salen su cola exclusiva y su routing key.
 */
public record ClientIdentity(String instanceId) {
    public ClientIdentity {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId must not be blank");
        }
    }

    public String replyQueue() {
        return "query_client." + instanceId;
    }

    public String routingKey() {
        return "query." + instanceId;
    }
}
