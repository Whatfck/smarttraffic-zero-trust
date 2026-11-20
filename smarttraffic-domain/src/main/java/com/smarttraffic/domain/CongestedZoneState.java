package com.smarttraffic.domain;

import static com.smarttraffic.domain.CongestionThresholds.*;

import java.util.List;

/** Estado CONGESTIONADA: baja directo a DESPEJADA si se vacia, o a MODERADA. */
public final class CongestedZoneState extends AbstractZoneState {
    private static final List<TransitionRule> RULES = List.of(
            TransitionRule.when(d -> d.below(CLEAR_DOWN), ClearZoneState::new),
            TransitionRule.when(d -> d.below(MODERATE_DOWN), ModerateZoneState::new));

    @Override
    public CongestionState level() {
        return CongestionState.CONGESTIONADA;
    }

    @Override
    protected List<TransitionRule> rules() {
        return RULES;
    }
}
