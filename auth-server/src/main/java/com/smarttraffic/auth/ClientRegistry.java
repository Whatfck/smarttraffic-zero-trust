package com.smarttraffic.auth;

import java.util.Optional;

/** Puerto (DIP): de donde salen las identidades de servicio registradas. */
public interface ClientRegistry {
    Optional<RegisteredClient> find(String clientId);
}
