package com.uctale.uctale.domain.game;

public record GameState(
        int turnNumber,
        PlayerCharacter playerCharacter,
        WorldState worldState,
        StoryMemory storyMemory,
        Inventory inventory,
        CombatEncounter combatEncounter,
        AbilityState abilityState,
        QuestState questState,
        RelationshipState relationshipState
) {
    public GameState {
        if (turnNumber < 1) throw new IllegalArgumentException("turnNumber는 1 이상이어야 합니다.");
        if (playerCharacter == null || worldState == null || storyMemory == null || inventory == null
                || abilityState == null || questState == null || relationshipState == null) {
            throw new IllegalArgumentException("GameState 구성 요소는 null일 수 없습니다.");
        }
        if (combatEncounter != null) CombatRules.validateState(combatEncounter, playerCharacter.vitals());
    }

    public GameState(int turnNumber, PlayerCharacter playerCharacter, WorldState worldState, StoryMemory storyMemory,
                     Inventory inventory, CombatEncounter combatEncounter, AbilityState abilityState, QuestState questState) {
        this(turnNumber, playerCharacter, worldState, storyMemory, inventory, combatEncounter, abilityState, questState,
                RelationshipState.empty());
    }

    public GameState(int turnNumber, PlayerCharacter playerCharacter, WorldState worldState, StoryMemory storyMemory,
                     Inventory inventory, CombatEncounter combatEncounter, AbilityState abilityState) {
        this(turnNumber, playerCharacter, worldState, storyMemory, inventory, combatEncounter, abilityState,
                QuestState.empty(), RelationshipState.empty());
    }

    public GameState(int turnNumber, PlayerCharacter playerCharacter, WorldState worldState, StoryMemory storyMemory,
                     Inventory inventory, CombatEncounter combatEncounter) {
        this(turnNumber, playerCharacter, worldState, storyMemory, inventory, combatEncounter, AbilityState.empty(),
                QuestState.empty(), RelationshipState.empty());
    }

    public GameState(int turnNumber, PlayerCharacter playerCharacter, WorldState worldState, StoryMemory storyMemory,
                     Inventory inventory) {
        this(turnNumber, playerCharacter, worldState, storyMemory, inventory, null, AbilityState.empty(),
                QuestState.empty(), RelationshipState.empty());
    }

    public GameState(int turnNumber, PlayerCharacter playerCharacter, WorldState worldState, StoryMemory storyMemory) {
        this(turnNumber, playerCharacter, worldState, storyMemory, Inventory.empty(), null, AbilityState.empty(),
                QuestState.empty(), RelationshipState.empty());
    }

    public static GameState initial(String worldSetting, String characterSetting, String openingStory) {
        return new GameState(1, PlayerCharacter.initial(characterSetting), WorldState.initial(worldSetting),
                StoryMemory.initial(worldSetting, characterSetting, openingStory), Inventory.empty(), null,
                AbilityState.empty(), QuestState.empty(), RelationshipState.empty());
    }

    public GameState withStoryMemory(StoryMemory nextStoryMemory) {
        if (nextStoryMemory == null) throw new IllegalArgumentException("storyMemory는 null일 수 없습니다.");
        return new GameState(turnNumber, playerCharacter, worldState, nextStoryMemory, inventory, combatEncounter,
                abilityState, questState, relationshipState);
    }

    public GameState withInventory(Inventory nextInventory) {
        if (nextInventory == null) throw new IllegalArgumentException("inventory는 null일 수 없습니다.");
        return new GameState(turnNumber, playerCharacter, worldState, storyMemory, nextInventory, combatEncounter,
                abilityState, questState, relationshipState);
    }

    public GameState withPlayerVitals(CharacterVitals nextVitals) {
        if (nextVitals == null) throw new IllegalArgumentException("player vitals는 null일 수 없습니다.");
        return withRuleState(inventory, nextVitals, combatEncounter, abilityState, questState, relationshipState);
    }

    public GameState withCombatEncounter(CombatEncounter nextCombatEncounter) {
        return withRuleState(inventory, playerCharacter.vitals(), nextCombatEncounter, abilityState, questState, relationshipState);
    }

    public GameState withAbilityState(AbilityState nextAbilityState) {
        if (nextAbilityState == null) throw new IllegalArgumentException("abilityState는 null일 수 없습니다.");
        return withRuleState(inventory, playerCharacter.vitals(), combatEncounter, nextAbilityState, questState, relationshipState);
    }

    public GameState withQuestState(QuestState nextQuestState) {
        if (nextQuestState == null) throw new IllegalArgumentException("questState는 null일 수 없습니다.");
        return withRuleState(inventory, playerCharacter.vitals(), combatEncounter, abilityState, nextQuestState, relationshipState);
    }

    public GameState withRelationshipState(RelationshipState nextRelationshipState) {
        if (nextRelationshipState == null) throw new IllegalArgumentException("relationshipState는 null일 수 없습니다.");
        return withRuleState(inventory, playerCharacter.vitals(), combatEncounter, abilityState, questState, nextRelationshipState);
    }

    public GameState withRuleState(Inventory nextInventory, CharacterVitals nextVitals, CombatEncounter nextCombatEncounter) {
        return withRuleState(nextInventory, nextVitals, nextCombatEncounter, abilityState, questState, relationshipState);
    }

    public GameState withRuleState(Inventory nextInventory, CharacterVitals nextVitals,
                                  CombatEncounter nextCombatEncounter, AbilityState nextAbilityState) {
        return withRuleState(nextInventory, nextVitals, nextCombatEncounter, nextAbilityState, questState, relationshipState);
    }

    public GameState withRuleState(Inventory nextInventory, CharacterVitals nextVitals,
                                  CombatEncounter nextCombatEncounter, AbilityState nextAbilityState,
                                  QuestState nextQuestState) {
        return withRuleState(nextInventory, nextVitals, nextCombatEncounter, nextAbilityState, nextQuestState, relationshipState);
    }

    public GameState withRuleState(Inventory nextInventory, CharacterVitals nextVitals,
                                  CombatEncounter nextCombatEncounter, AbilityState nextAbilityState,
                                  QuestState nextQuestState, RelationshipState nextRelationshipState) {
        if (nextInventory == null || nextVitals == null || nextAbilityState == null || nextQuestState == null
                || nextRelationshipState == null) {
            throw new IllegalArgumentException("rule state 구성 요소는 null일 수 없습니다.");
        }
        return new GameState(turnNumber, playerCharacter.withVitals(nextVitals), worldState, storyMemory,
                nextInventory, nextCombatEncounter, nextAbilityState, nextQuestState, nextRelationshipState);
    }

    public GameState advanceTurn() {
        return new GameState(turnNumber + 1, playerCharacter, worldState, storyMemory, inventory, combatEncounter,
                abilityState, questState, relationshipState);
    }

    public GameState recordNarrativeTurn(String playerAction, String storyText) {
        if (!storyMemory.recentTurns().isEmpty() && storyMemory.recentTurns().getLast().turnNumber() >= turnNumber) {
            throw new IllegalStateException("현재 turn의 narrative가 이미 기록되어 있습니다.");
        }
        return new GameState(turnNumber, playerCharacter, worldState,
                storyMemory.append(new GameTurn(turnNumber, playerAction, storyText)), inventory, combatEncounter,
                abilityState, questState, relationshipState);
    }

    public GameState advance(String playerAction, String storyText) {
        return advanceTurn().recordNarrativeTurn(playerAction, storyText);
    }
}
