package com.smarttraffic.domain;

public final class CongestedState implements CongestionTransition {
    @Override
    public CongestionState resolve(double density) {
        return density < 0.55 ? CongestionState.MODERADA : CongestionState.CONGESTIONADA;
    }
}
