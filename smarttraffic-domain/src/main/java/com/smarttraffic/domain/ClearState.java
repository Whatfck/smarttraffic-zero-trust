package com.smarttraffic.domain;

public final class ClearState implements CongestionTransition {
    @Override
    public CongestionState resolve(double density) {
        return density >= 0.70 ? CongestionState.CONGESTIONADA
                : density >= 0.35 ? CongestionState.MODERADA : CongestionState.DESPEJADA;
    }
}
