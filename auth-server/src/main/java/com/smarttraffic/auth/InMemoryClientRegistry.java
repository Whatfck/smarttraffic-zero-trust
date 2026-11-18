package com.smarttraffic.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Adaptador: registro de clientes construido desde la configuracion (fail-fast al arrancar). */
@Component
public final class InMemoryClientRegistry implements ClientRegistry {
    private final Map<String, RegisteredClient> clients;

    public InMemoryClientRegistry(ClientsProperties properties) {
        this.clients = properties.clients().entrySet().stream()
                .map(entry -> new RegisteredClient(entry.getKey(),
                        entry.getValue().secret(),
                        orEmpty(entry.getValue().audiences()),
                        orEmpty(entry.getValue().scopes()),
                        orEmpty(entry.getValue().roles())))
                .collect(Collectors.toUnmodifiableMap(RegisteredClient::clientId, client -> client));
    }

    @Override
    public Optional<RegisteredClient> find(String clientId) {
        return Optional.ofNullable(clientId).map(clients::get);
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }
}
