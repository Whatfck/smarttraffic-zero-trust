package com.smarttraffic.domain;

import static com.smarttraffic.domain.CongestionThresholds.*;

import java.util.List;

/** Estado DESPEJADA: solo puede empeorar. */
public final class ClearZoneState extends AbstractZoneState {
    private static final List<TransitionRule> RULES = List.of(
            TransitionRule.when(d -> d.atLeast(CONGESTED_UP), CongestedZoneState::new),
            TransitionRule.when(d -> d.atLeast(MODERATE_UP), ModerateZoneState::new));

    @Override
    public CongestionState level() {
        return CongestionState.DESPEJADA;
    }

    @Override
    protected List<TransitionRule> rules() {
        return RULES;
    }
}
