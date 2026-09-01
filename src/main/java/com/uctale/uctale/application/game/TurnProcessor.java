package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.RandomSource;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.TurnResolution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class TurnProcessor {

    private final ActionResolver actionResolver;
    private final SkillCheckDecisionService skillCheckDecisionService;
    private final RandomSource randomSource;

    public TurnProcessor() {
        this.actionResolver = new ActionResolver();
        this.skillCheckDecisionService = null;
        this.randomSource = null;
    }

    @Autowired
    public TurnProcessor(SkillCheckDecisionService skillCheckDecisionService, RandomSource randomSource) {
        this.actionResolver = new ActionResolver();
        this.skillCheckDecisionService = skillCheckDecisionService;
        this.randomSource = randomSource;
    }

    public TurnResolution resolve(GameState state, PlayerAction action) {
        return actionResolver.resolve(state, action);
    }

    public TurnResolution resolve(
            GameState state,
            PlayerAction action,
            Long requestId,
            String reservationOwner
    ) {
        if (!actionResolver.requiresSkillCheck(action)) {
            return actionResolver.resolve(state, action);
        }
        if (skillCheckDecisionService == null || randomSource == null) {
            throw new IllegalStateException("Skill Check turn processor가 persistence/random source와 연결되지 않았습니다.");
        }
        SkillCheckResult result = skillCheckDecisionService.getOrCreate(
                requestId,
                reservationOwner,
                () -> actionResolver.rollSkillCheck(state, action, randomSource)
        );
        return actionResolver.resolve(state, action, result);
    }

    public StateTransition attachNarrative(TurnResolution resolution, String storyText) {
        if (resolution == null) {
            throw new IllegalArgumentException("TurnResolution은 필수입니다.");
        }
        return resolution.attachNarrative(storyText);
    }
}
