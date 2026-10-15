package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameplayActionRulesTest {
    private final ActionResolver resolver = new ActionResolver();
    private final QuestRules quests = new QuestRules();
    private final RelationshipRules relationships = new RelationshipRules();

    private PlayerAction action(int turn, int choice, String intent, String subject) {
        return new PlayerAction(choice, "issued-token-" + turn, ActionType.GAMEPLAY, turn,
                GameplayActionRules.issueArguments(choice, intent, subject), "행동한다");
    }

    @Test
    void collectUsesServerQuantityAndStableIdentity() {
        GameState state = GameState.initial("world", "hero", "start");
        TurnResolution first = GameplayActionRules.resolve(state, action(1, 3, "COLLECT", "herb"), resolver, quests, relationships);
        assertThat(first.stateTransition().nextState().inventory().requireItem("item:herb").quantity()).isEqualTo(1);
        assertThat(first.gameResult().stateChanges()).anyMatch(GameResult.ItemAcquired.class::isInstance);
        TurnResolution second = GameplayActionRules.resolve(first.stateTransition().nextState(),
                action(2, 4, "COLLECT", "herb"), resolver, quests, relationships);
        assertThat(second.stateTransition().nextState().inventory().requireItem("item:herb").quantity()).isEqualTo(2);
    }

    @Test
    void restAtFullResourcesDoesNotOverflow() {
        GameState state = GameState.initial("world", "hero", "start");
        TurnResolution resolved = GameplayActionRules.resolve(state, action(1, 1, "REST", "camp"), resolver, quests, relationships);
        assertThat(resolved.stateTransition().nextState().playerCharacter().vitals()).isEqualTo(state.playerCharacter().vitals());
    }

    @Test
    void talkUpdatesStableNpcWithoutProviderNumbers() {
        GameState state = GameState.initial("world", "hero", "start");
        TurnResolution resolved = GameplayActionRules.resolve(state, action(1, 1, "TALK", "guide"), resolver, quests, relationships);
        assertThat(resolved.stateTransition().nextState().relationshipState().find("npc:guide")).isNotNull();
        assertThat(resolved.gameResult().stateChanges()).anyMatch(GameResult.RelationshipChanged.class::isInstance);
    }

    @Test
    void questCanActivateAndCompleteWithCanonicalCollectionFlag() {
        GameState initial = GameState.initial("world", "hero", "start");
        TurnResolution started = GameplayActionRules.resolve(initial,
                action(1, 1, "START_QUEST", "herb"), resolver, quests, relationships);
        assertThat(started.stateTransition().nextState().questState().quests().get("quest.v2.herb").status())
                .isEqualTo(QuestStatus.ACTIVE);
        TurnResolution finished = GameplayActionRules.resolve(started.stateTransition().nextState(),
                action(2, 2, "COLLECT", "herb"), resolver, quests, relationships);
        assertThat(finished.stateTransition().nextState().questState().quests().get("quest.v2.herb").status())
                .isEqualTo(QuestStatus.COMPLETED);
        assertThat(finished.gameResult().stateChanges()).anyMatch(GameResult.FlagChanged.class::isInstance);
        assertThat(finished.stateTransition().nextState().questState().eventFlags())
                .containsKey("quest.completed.quest.v2.herb");
    }

    @Test
    void equipmentAndStatusAreTypeSafeCanonicalMutations() {
        GameState initial = GameState.initial("world", "hero", "start");
        TurnResolution found = GameplayActionRules.resolve(initial, action(1, 1, "FIND_WEAPON", "dagger"), resolver, quests, relationships);
        assertThat(found.stateTransition().nextState().inventory().requireItem("item:weapon:dagger").definition().combatModifiers().attackBonus()).isEqualTo(1);
        TurnResolution equipped = GameplayActionRules.resolve(found.stateTransition().nextState(),
                action(2, 2, "EQUIP_WEAPON", "dagger"), resolver, quests, relationships);
        assertThat(equipped.stateTransition().nextState().inventory().equipment().itemIdAt(EquipmentSlot.MAIN_HAND))
                .isEqualTo("item:weapon:dagger");
        assertThat(equipped.gameResult().stateChanges()).anyMatch(GameResult.ItemEquipped.class::isInstance);
        TurnResolution status = GameplayActionRules.resolve(equipped.stateTransition().nextState(),
                action(3, 3, "APPLY_STATUS", "focused"), resolver, quests, relationships);
        assertThat(status.stateTransition().nextState().playerCharacter().vitals().statusEffects()).containsKey("status:focused");
        assertThat(status.gameResult().stateChanges()).anyMatch(GameResult.StatusEffectApplied.class::isInstance);
        assertThatThrownBy(() -> GameplayActionRules.resolve(initial, action(1, 4, "EQUIP_WEAPON", "missing"),
                resolver, quests, relationships)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void combatStartCreatesAuditedActiveEncounter() {
        GameState state = GameState.initial("world", "hero", "start");
        TurnResolution started = GameplayActionRules.resolve(state, action(1, 1, "START_COMBAT", "wolf"),
                resolver, quests, relationships);
        assertThat(started.stateTransition().nextState().combatEncounter().active()).isTrue();
        assertThat(started.gameResult().stateChanges())
                .filteredOn(GameResult.CombatEncounterChanged.class::isInstance).hasSize(2);
        assertThat(CombatRules.replay(state.combatEncounter(), started.gameResult().stateChanges(),
                started.stateTransition().nextState().playerCharacter().vitals()))
                .isEqualTo(started.stateTransition().nextState().combatEncounter());
    }

    @Test
    void dynamicAbilityIsBoundedAndIssuedForMaterializedEnemy() {
        GameState initial = GameState.initial("world", "hero", "start");
        GameState active = GameplayActionRules.resolve(initial,
                action(1, 1, "START_COMBAT", "wolf"), resolver, quests, relationships)
                .stateTransition().nextState();
        var available = new com.uctale.uctale.application.game.CombatActionIssuer().issue(active).stream()
                .filter(choice -> "ability.v2.wolf".equals(choice.arguments().get("abilityDefinitionId")))
                .findFirst().orElseThrow();
        PlayerAction use = new PlayerAction(available.legacyChoiceId(), available.token(), available.type(),
                available.sourceTurn(), available.arguments(), available.displayText());
        TurnResolution used = resolver.resolve(active, use);
        assertThat(used.stateTransition().nextState().playerCharacter().vitals().mp().current()).isEqualTo(8);
        assertThat(used.stateTransition().nextState().abilityState().remainingCooldown("ability.v2.wolf")).isEqualTo(2);
        assertThat(used.stateTransition().nextState().combatEncounter().requireEnemy("enemy:wolf")
                .vitals().hp().current()).isEqualTo(7);
        assertThatThrownBy(() -> AbilityDefinitions.require("ability.v2.bad/path"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidAndTamperedProposals() {
        assertThatThrownBy(() -> GameplayActionRules.issueArguments(1, "CHEAT", "guide")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GameplayActionRules.validateProposal("TALK", "../guide")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GameplayActionRules.validateProposal(null, "guide")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GameplayActionRules.validateProposal("TALK", "A".repeat(300))).isInstanceOf(IllegalArgumentException.class);
        PlayerAction forged = new PlayerAction(1, "token", ActionType.GAMEPLAY, 1,
                Map.of("choiceId", "1", "intent", "TALK", "subjectKey", "guide", "rulesetVersion", "999"), "hello");
        assertThatThrownBy(() -> GameplayActionRules.resolve(GameState.initial("w","p","s"), forged, resolver, quests, relationships))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
