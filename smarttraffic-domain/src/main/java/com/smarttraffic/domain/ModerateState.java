package com.smarttraffic.domain;

public final class ModerateState implements CongestionTransition {
    @Override
    public CongestionState resolve(double density) {
        return density >= 0.70 ? CongestionState.CONGESTIONADA
                : density < 0.25 ? CongestionState.DESPEJADA : CongestionState.MODERADA;
    }
}
