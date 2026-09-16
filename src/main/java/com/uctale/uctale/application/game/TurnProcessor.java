package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.ActionResolver;
import com.uctale.uctale.domain.game.AttackResult;
import com.uctale.uctale.domain.game.GameState;
import com.uctale.uctale.domain.game.InventoryCommand;
import com.uctale.uctale.domain.game.QuestRules;
import com.uctale.uctale.domain.game.RandomSource;
import com.uctale.uctale.domain.game.RelationshipCommand;
import com.uctale.uctale.domain.game.RelationshipRules;
import com.uctale.uctale.domain.game.SkillCheckResult;
import com.uctale.uctale.domain.game.StateTransition;
import com.uctale.uctale.domain.game.TurnResolution;
import com.uctale.uctale.domain.game.VitalsCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class TurnProcessor {

    private final ActionResolver actionResolver;
    private final QuestRules questRules;
    private final RelationshipRules relationshipRules;
    private final SkillCheckDecisionService skillCheckDecisionService;
    private final AttackDecisionService attackDecisionService;
    private final RandomSource randomSource;

    public TurnProcessor() {
        this.actionResolver = new ActionResolver();
        this.questRules = new QuestRules();
        this.relationshipRules = new RelationshipRules();
        this.skillCheckDecisionService = null;
        this.attackDecisionService = null;
        this.randomSource = null;
    }

    public TurnProcessor(SkillCheckDecisionService skillCheckDecisionService, RandomSource randomSource) {
        this.actionResolver = new ActionResolver();
        this.questRules = new QuestRules();
        this.relationshipRules = new RelationshipRules();
        this.skillCheckDecisionService = skillCheckDecisionService;
        this.attackDecisionService = null;
        this.randomSource = randomSource;
    }

    @Autowired
    public TurnProcessor(
            SkillCheckDecisionService skillCheckDecisionService,
            AttackDecisionService attackDecisionService,
            RandomSource randomSource
    ) {
        this.actionResolver = new ActionResolver();
        this.questRules = new QuestRules();
        this.relationshipRules = new RelationshipRules();
        this.skillCheckDecisionService = skillCheckDecisionService;
        this.attackDecisionService = attackDecisionService;
        this.randomSource = randomSource;
    }

    public TurnResolution resolve(GameState state, PlayerAction action) {
        return questRules.apply(actionResolver.resolve(state, action));
    }

    public TurnResolution resolveWithRelationships(GameState state, PlayerAction action,
                                                   List<RelationshipCommand> relationshipCommands) {
        return relationshipRules.apply(resolve(state, action), relationshipCommands);
    }

    public TurnResolution resolveWithInventory(GameState state, PlayerAction action, List<InventoryCommand> inventoryCommands) {
        return questRules.apply(actionResolver.resolveWithInventory(state, action, inventoryCommands));
    }

    public TurnResolution resolveWithEffects(GameState state, PlayerAction action,
                                             List<InventoryCommand> inventoryCommands,
                                             List<VitalsCommand> vitalsCommands) {
        return questRules.apply(actionResolver.resolveWithEffects(state, action, inventoryCommands, vitalsCommands));
    }

    public TurnResolution resolveWithEffects(GameState state, PlayerAction action,
                                             List<InventoryCommand> inventoryCommands,
                                             List<VitalsCommand> vitalsCommands,
                                             List<RelationshipCommand> relationshipCommands) {
        return relationshipRules.apply(resolveWithEffects(state, action, inventoryCommands, vitalsCommands), relationshipCommands);
    }

    public TurnResolution resolve(
            GameState state,
            PlayerAction action,
            Long requestId,
            String reservationOwner
    ) {
        if (actionResolver.requiresAttackRoll(action)) {
            if (attackDecisionService == null || randomSource == null) {
                throw new IllegalStateException("Attack turn processor가 persistence/random source와 연결되지 않았습니다.");
            }
            AttackResult result = attackDecisionService.getOrCreate(
                    requestId,
                    reservationOwner,
                    () -> actionResolver.rollAttack(state, action, randomSource)
            );
            return questRules.apply(actionResolver.resolveAttack(state, action, result));
        }
        if (!actionResolver.requiresSkillCheck(action)) {
            return questRules.apply(actionResolver.resolve(state, action));
        }
        if (skillCheckDecisionService == null || randomSource == null) {
            throw new IllegalStateException("Skill Check turn processor가 persistence/random source와 연결되지 않았습니다.");
        }
        SkillCheckResult result = skillCheckDecisionService.getOrCreate(
                requestId,
                reservationOwner,
                () -> actionResolver.rollSkillCheck(state, action, randomSource)
        );
        return questRules.apply(actionResolver.resolve(state, action, result));
    }

    public StateTransition attachNarrative(TurnResolution resolution, String storyText) {
        if (resolution == null) throw new IllegalArgumentException("TurnResolution은 필수입니다.");
        return resolution.attachNarrative(storyText);
    }
}
