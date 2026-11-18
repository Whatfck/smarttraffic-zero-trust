package com.smarttraffic.domain;

import static com.smarttraffic.domain.CongestionThresholds.*;

import java.util.List;

/** Estado MODERADA: puede empeorar a CONGESTIONADA o mejorar a DESPEJADA. */
public final class ModerateZoneState extends AbstractZoneState {
    private static final List<TransitionRule> RULES = List.of(
            TransitionRule.when(d -> d.atLeast(CONGESTED_UP), CongestedZoneState::new),
            TransitionRule.when(d -> d.below(CLEAR_DOWN), ClearZoneState::new));

    @Override
    public CongestionState level() {
        return CongestionState.MODERADA;
    }

    @Override
    protected List<TransitionRule> rules() {
        return RULES;
    }
}
