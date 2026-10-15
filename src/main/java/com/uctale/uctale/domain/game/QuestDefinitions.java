package com.uctale.uctale.domain.game;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class QuestDefinitions {
    public static final String FIXTURE_QUEST_ID = "fixture.pathfinder";
    public static final String COLLECT_OBJECTIVE_ID = "collect-supplies";
    public static final String DIALOGUE_OBJECTIVE_ID = "speak-guide";
    public static final String COMBAT_OBJECTIVE_ID = "win-combat";
    public static final String COLLECTION_ITEM_DEFINITION_ID = "quest-supply";
    public static final int DIALOGUE_CHOICE_ID = 1;

    private static final Map<String, QuestDefinition> DEFINITIONS = definitions();
    private static final String DYNAMIC_PREFIX = "quest.v2.";
    private static final Pattern SUBJECT = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    private QuestDefinitions() {}

    public static QuestDefinition fixture() {
        return DEFINITIONS.get(FIXTURE_QUEST_ID);
    }

    public static Optional<QuestDefinition> find(String definitionId) {
        QuestDefinition registered = DEFINITIONS.get(definitionId);
        if (registered != null) return Optional.of(registered);
        if (definitionId == null || !definitionId.startsWith(DYNAMIC_PREFIX)) return Optional.empty();
        String subject = definitionId.substring(DYNAMIC_PREFIX.length());
        if (!SUBJECT.matcher(subject).matches()) return Optional.empty();
        return Optional.of(new QuestDefinition(definitionId, Map.of(
                "gather", new ObjectiveDefinition("gather", ObjectiveProgressType.COUNT,
                        ObjectiveTrigger.COLLECTION, subject, null, 1)
        )));
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
