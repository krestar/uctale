package com.uctale.uctale.domain.game;

import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Server-owned, bounded bridge from optional narrative intents to canonical commands.
 * The provider can propose an intent and identity; all effect sizes are fixed here.
 */
public final class GameplayActionRules {
    private static final Pattern SUBJECT = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final String VERSION = Integer.toString(GameRuleset.CURRENT_VERSION);
    private static final int REST_AMOUNT = 2;

    public enum Intent { COLLECT, REST, TALK, START_QUEST, START_COMBAT, FIND_WEAPON, EQUIP_WEAPON, UNEQUIP_WEAPON, APPLY_STATUS }

    private GameplayActionRules() {}

    public static void validateProposal(String intent, String subjectKey) {
        if (intent == null && subjectKey == null) return;
        if (intent == null || subjectKey == null) {
            throw new IllegalArgumentException("intent와 subjectKey는 함께 제공해야 합니다.");
        }
        try {
            Intent.valueOf(intent);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("지원하지 않는 gameplay intent입니다.", exception);
        }
        if (!SUBJECT.matcher(subjectKey).matches()) {
            throw new IllegalArgumentException("gameplay subjectKey는 최대 32자 영문 slug여야 합니다.");
        }
    }

    public static Map<String, String> issueArguments(int choiceId, String intent, String subjectKey) {
        if (choiceId < 1) throw new IllegalArgumentException("choiceId는 양수여야 합니다.");
        validateProposal(intent, subjectKey);
        if (intent == null) throw new IllegalArgumentException("gameplay intent가 필요합니다.");
        return Map.of("choiceId", Integer.toString(choiceId), "intent", intent,
                "subjectKey", subjectKey, "rulesetVersion", VERSION);
    }

    public static void validate(PlayerAction action) {
        Objects.requireNonNull(action, "action은 필수입니다.");
        if (action.type() != ActionType.GAMEPLAY || action.token() == null || action.token().isBlank()) {
            throw new IllegalArgumentException("유효한 server-issued gameplay action이 필요합니다.");
        }
        Map<String, String> args = action.arguments();
        if (args.size() != 4 || !Integer.toString(action.legacyChoiceId()).equals(args.get("choiceId"))
                || !VERSION.equals(args.get("rulesetVersion"))) {
            throw new IllegalArgumentException("gameplay action arguments/ruleset이 올바르지 않습니다.");
        }
        validateProposal(args.get("intent"), args.get("subjectKey"));
        if (args.get("intent") == null) throw new IllegalArgumentException("gameplay intent가 누락되었습니다.");
    }

    public static TurnResolution resolve(GameState state, PlayerAction action, ActionResolver resolver,
                                         QuestRules quests, RelationshipRules relationships) {
        Objects.requireNonNull(state, "GameState는 필수입니다.");
        validate(action);
        if (action.sourceTurn() != state.turnNumber()) {
            throw new IllegalArgumentException("gameplay action이 오래된 turn을 참조합니다.");
        }
        Intent intent = Intent.valueOf(action.arguments().get("intent"));
        String key = action.arguments().get("subjectKey");
        List<InventoryCommand> inventory = new ArrayList<>();
        List<VitalsCommand> vitals = new ArrayList<>();
        List<RelationshipCommand> relationship = new ArrayList<>();

        switch (intent) {
            case COLLECT -> {
                inventory.add(new InventoryCommand.Acquire(new OwnedItem("item:" + key,
                        new ItemDefinition(key, ItemOwnershipType.STACK, null), 1)));
            }
            case FIND_WEAPON -> inventory.add(new InventoryCommand.Acquire(new OwnedItem(
                    "item:weapon:" + key,
                    new ItemDefinition("weapon:" + key, ItemOwnershipType.INSTANCE, EquipmentSlot.MAIN_HAND,
                            new ItemCombatModifiers(1, 1)), 1)));
            case EQUIP_WEAPON -> inventory.add(new InventoryCommand.Equip("item:weapon:" + key, EquipmentSlot.MAIN_HAND));
            case UNEQUIP_WEAPON -> {
                String equipped = state.inventory().equipment().itemIdAt(EquipmentSlot.MAIN_HAND);
                if (!("item:weapon:" + key).equals(equipped)) {
                    throw new IllegalArgumentException("현재 장착된 무기와 행동 대상이 일치하지 않습니다.");
                }
                inventory.add(new InventoryCommand.Unequip(EquipmentSlot.MAIN_HAND));
            }
            case APPLY_STATUS -> vitals.add(new VitalsCommand.ApplyStatus(
                    new StatusEffect("status:" + key, 1, 1, 2, StatusExpiryTrigger.END_OF_TURN, false)));
            case REST -> {
                CharacterVitals current = state.playerCharacter().vitals();
                if (current.hp().current() < current.hp().max()) vitals.add(new VitalsCommand.Heal(REST_AMOUNT));
                if (current.mp().current() < current.mp().max()) vitals.add(new VitalsCommand.RestoreMana(REST_AMOUNT));
            }
            case START_QUEST, START_COMBAT -> { /* server-owned state transition is appended below */ }
            case TALK -> relationship.add(RelationshipCommand.talk(
                    new NpcIdentity("npc:" + key, "npc:" + key), 1, state.turnNumber(), action.token()));
        }

        TurnResolution base = resolver.resolveWithEffects(state, action, inventory, vitals);
        if (intent == Intent.START_QUEST) {
            String definitionId = "quest.v2." + key;
            QuestRules.Result activation = quests.activate(base.stateTransition().nextState().questState(), definitionId);
            List<GameResult.StateChange> changes = new ArrayList<>(base.gameResult().stateChanges());
            int turnIndex = -1;
            for (int i = 0; i < changes.size(); i++) {
                if (changes.get(i) instanceof GameResult.TurnAdvanced) {
                    turnIndex = i;
                    break;
                }
            }
            if (turnIndex < 0) throw new IllegalStateException("TURN_ADVANCED audit이 없습니다.");
            changes.addAll(turnIndex, activation.stateChanges());
            GameResult result = base.gameResult();
            List<GameResult.GameEvent> events = new ArrayList<>(result.events());
            events.add(GameResult.GameEvent.QUEST_UPDATED);
            GameResult decorated = new GameResult(result.resolvedAction(), result.outcome(), result.skillCheckResult(),
                    result.attackResult(), result.canonicalFacts(), events, changes, result.narrativeCues());
            GameState next = base.stateTransition().nextState().withQuestState(activation.questState());
            base = new TurnResolution(decorated, new StateTransition(state, next));
        }
        if (intent == Intent.START_COMBAT) {
            String encounterId = "encounter:v2:" + state.turnNumber() + ":" + key;
            EnemyState enemy = new EnemyState("enemy:" + key, key.replace('-', ' '), CharacterVitals.defaults());
            CombatRules.Result started = CombatRules.start(base.stateTransition().nextState().combatEncounter(),
                    encounterId, List.of(enemy));
            CombatRules.Result activated = CombatRules.activate(started.encounter(),
                    base.stateTransition().nextState().playerCharacter().vitals());
            List<GameResult.StateChange> changes = new ArrayList<>(base.gameResult().stateChanges());
            int turnIndex = -1;
            for (int i = 0; i < changes.size(); i++) {
                if (changes.get(i) instanceof GameResult.TurnAdvanced) {
                    turnIndex = i;
                    break;
                }
            }
            if (turnIndex < 0) throw new IllegalStateException("TURN_ADVANCED audit이 없습니다.");
            changes.addAll(turnIndex, started.stateChanges());
            changes.addAll(turnIndex + started.stateChanges().size(), activated.stateChanges());
            GameResult result = base.gameResult();
            List<GameResult.GameEvent> events = new ArrayList<>(result.events());
            events.add(GameResult.GameEvent.COMBAT_ENCOUNTER_CHANGED);
            GameResult decorated = new GameResult(result.resolvedAction(), result.outcome(), result.skillCheckResult(),
                    result.attackResult(), result.canonicalFacts(), events, changes, result.narrativeCues());
            GameState next = base.stateTransition().nextState().withCombatEncounter(activated.encounter());
            base = new TurnResolution(decorated, new StateTransition(state, next));
        }
        return relationships.apply(quests.apply(base), relationship);
    }
}
