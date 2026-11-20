package com.smarttraffic.domain;

/**
 * Umbrales de densidad. Se usa HISTERESIS: para bajar de nivel se exige una densidad
 * menor que la necesaria para subir, evitando que una zona "parpadee" entre estados.
 */
final class CongestionThresholds {
    static final double MODERATE_UP = 0.35;    // DESPEJADA -> MODERADA
    static final double CONGESTED_UP = 0.70;   // * -> CONGESTIONADA
    static final double MODERATE_DOWN = 0.55;  // CONGESTIONADA -> MODERADA
    static final double CLEAR_DOWN = 0.25;     // MODERADA -> DESPEJADA

    private CongestionThresholds() {}
}
