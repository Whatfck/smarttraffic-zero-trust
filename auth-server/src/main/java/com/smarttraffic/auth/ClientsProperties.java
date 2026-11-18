package com.smarttraffic.auth;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identidades declaradas en application.yml (smarttraffic.clients.*). Los secretos
 * llegan por variables de entorno generadas en el aprovisionamiento.
 */
@ConfigurationProperties(prefix = "smarttraffic")
public record ClientsProperties(Map<String, Definition> clients) {

    public ClientsProperties {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    public record Definition(String secret, List<String> audiences, List<String> scopes, List<String> roles) {}
}
