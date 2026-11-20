package com.smarttraffic.domain;

/**
 * Nivel de congestion publicado en los mensajes (contrato estable entre servicios).
 * El ORDEN importa: de menor a mayor gravedad (se usa para obtener el peor tramo).
 * El comportamiento de transicion vive en las clases del patron State
 * ({@link TrafficZoneState}), no aqui.
 */
public enum CongestionState {
    DESPEJADA,
    MODERADA,
    CONGESTIONADA;

    public boolean isCritical() {
        return this == CONGESTIONADA;
    }

    public CongestionState worst(CongestionState other) {
        return other.ordinal() > ordinal() ? other : this;
    }
}
