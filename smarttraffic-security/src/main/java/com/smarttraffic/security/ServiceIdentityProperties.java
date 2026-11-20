package com.smarttraffic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identidad S2S del microservicio: con que client_id/secret pide su JWT y a que
 * Autoridad (vm-auth). Se configura con smarttraffic.identity.* o variables de entorno.
 */
@ConfigurationProperties(prefix = "smarttraffic.identity")
public record ServiceIdentityProperties(String tokenEndpoint, String clientId, String clientSecret) {
    public ServiceIdentityProperties {
        if (tokenEndpoint == null || tokenEndpoint.isBlank()) {
            throw new IllegalArgumentException("smarttraffic.identity.token-endpoint is required");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("smarttraffic.identity.client-id is required");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("smarttraffic.identity.client-secret is required");
        }
    }

    @Override
    public String toString() {
        return "ServiceIdentity[" + clientId + " @ " + tokenEndpoint + "]";
    }
}
