package com.smarttraffic.domain;

/**
 * Patron STATE: cada estado de una zona encapsula SUS propias reglas de transicion.
 * El agregado solo delega: {@code state = state.next(density)}; no hay if-else ni
 * switch sobre el estado actual. Para agregar un estado nuevo basta crear otra clase.
 */
public interface TrafficZoneState {

    /** Nivel publicado en los mensajes. */
    CongestionState level();

    /** Devuelve el estado siguiente segun la densidad medida (puede ser el mismo). */
    TrafficZoneState next(TrafficDensity density);

    /** Reconstruye el objeto estado a partir del nivel guardado (p. ej. en la KTable). */
    static TrafficZoneState of(CongestionState level) {
        return StateCatalog.forLevel(level);
    }
}
