package com.uctale.uctale.domain.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class QuestDefinitions {
    public static final String FIXTURE_QUEST_ID = "fixture.pathfinder";
    public static final String COLLECT_OBJECTIVE_ID = "collect-supplies";
    public static final String DIALOGUE_OBJECTIVE_ID = "speak-guide";
    public static final String COMBAT_OBJECTIVE_ID = "win-combat";
    public static final String COLLECTION_ITEM_DEFINITION_ID = "quest-supply";
    public static final int DIALOGUE_CHOICE_ID = 1;

    private static final Map<String, QuestDefinition> DEFINITIONS = definitions();

    private QuestDefinitions() {}

    public static QuestDefinition fixture() {
        return DEFINITIONS.get(FIXTURE_QUEST_ID);
    }

    public static Optional<QuestDefinition> find(String definitionId) {
        return Optional.ofNullable(DEFINITIONS.get(definitionId));
    }

    private static Map<String, QuestDefinition> definitions() {
        LinkedHashMap<String, ObjectiveDefinition> objectives = new LinkedHashMap<>();
        objectives.put(COLLECT_OBJECTIVE_ID, new ObjectiveDefinition(
                COLLECT_OBJECTIVE_ID, ObjectiveProgressType.COUNT, ObjectiveTrigger.COLLECTION,
                COLLECTION_ITEM_DEFINITION_ID, null, 2));
        objectives.put(DIALOGUE_OBJECTIVE_ID, new ObjectiveDefinition(
                DIALOGUE_OBJECTIVE_ID, ObjectiveProgressType.BOOLEAN, ObjectiveTrigger.DIALOGUE,
                "choiceId", Integer.toString(DIALOGUE_CHOICE_ID), 1));
        objectives.put(COMBAT_OBJECTIVE_ID, new ObjectiveDefinition(
                COMBAT_OBJECTIVE_ID, ObjectiveProgressType.STATE_MATCH, ObjectiveTrigger.COMBAT,
                "combatStatus", CombatEncounterStatus.RESOLVED.name(), 1));
        QuestDefinition fixture = new QuestDefinition(FIXTURE_QUEST_ID, objectives);
        return Map.of(fixture.definitionId(), fixture);
    }
}
