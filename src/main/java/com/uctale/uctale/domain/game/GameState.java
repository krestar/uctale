package com.uctale.uctale.domain.game;

public record GameState(
        int turnNumber,
        PlayerCharacter playerCharacter,
        WorldState worldState,
        StoryMemory storyMemory,
        Inventory inventory,
        CombatEncounter combatEncounter,
        AbilityState abilityState
) {
    public GameState {
        if (turnNumber < 1) throw new IllegalArgumentException("turnNumber는 1 이상이어야 합니다.");
        if (playerCharacter == null || worldState == null || storyMemory == null || inventory == null || abilityState == null) {
            throw new IllegalArgumentException("GameState 구성 요소는 null일 수 없습니다.");
        }
        if (combatEncounter != null) CombatRules.validateState(combatEncounter, playerCharacter.vitals());
    }

    public GameState(int turnNumber, PlayerCharacter playerCharacter, WorldState worldState, StoryMemory storyMemory,
                     Inventory inventory, CombatEncounter combatEncounter) {
        this(turnNumber, playerCharacter, worldState, storyMemory, inventory, combatEncounter, AbilityState.empty());
    }

    public GameState(int turnNumber, PlayerCharacter playerCharacter, WorldState worldState, StoryMemory storyMemory,
                     Inventory inventory) {
        this(turnNumber, playerCharacter, worldState, storyMemory, inventory, null, AbilityState.empty());
    }

    public GameState(int turnNumber, PlayerCharacter playerCharacter, WorldState worldState, StoryMemory storyMemory) {
        this(turnNumber, playerCharacter, worldState, storyMemory, Inventory.empty(), null, AbilityState.empty());
    }

    public static GameState initial(String worldSetting, String characterSetting, String openingStory) {
        return new GameState(1, PlayerCharacter.initial(characterSetting), WorldState.initial(worldSetting),
                StoryMemory.initial(worldSetting, characterSetting, openingStory), Inventory.empty(), null, AbilityState.empty());
    }

    public GameState withInventory(Inventory nextInventory) {
        if (nextInventory == null) throw new IllegalArgumentException("inventory는 null일 수 없습니다.");
        return new GameState(turnNumber, playerCharacter, worldState, storyMemory, nextInventory, combatEncounter, abilityState);
    }

    public GameState withPlayerVitals(CharacterVitals nextVitals) {
        if (nextVitals == null) throw new IllegalArgumentException("player vitals는 null일 수 없습니다.");
        return withRuleState(inventory, nextVitals, combatEncounter, abilityState);
    }

    public GameState withCombatEncounter(CombatEncounter nextCombatEncounter) {
        return withRuleState(inventory, playerCharacter.vitals(), nextCombatEncounter, abilityState);
    }

    public GameState withAbilityState(AbilityState nextAbilityState) {
        if (nextAbilityState == null) throw new IllegalArgumentException("abilityState는 null일 수 없습니다.");
        return withRuleState(inventory, playerCharacter.vitals(), combatEncounter, nextAbilityState);
    }

    public GameState withRuleState(Inventory nextInventory, CharacterVitals nextVitals, CombatEncounter nextCombatEncounter) {
        return withRuleState(nextInventory, nextVitals, nextCombatEncounter, abilityState);
    }

    public GameState withRuleState(Inventory nextInventory, CharacterVitals nextVitals,
                                  CombatEncounter nextCombatEncounter, AbilityState nextAbilityState) {
        if (nextInventory == null || nextVitals == null || nextAbilityState == null) {
            throw new IllegalArgumentException("rule state 구성 요소는 null일 수 없습니다.");
        }
        return new GameState(turnNumber, playerCharacter.withVitals(nextVitals), worldState, storyMemory,
                nextInventory, nextCombatEncounter, nextAbilityState);
    }

    public GameState advanceTurn() {
        return new GameState(turnNumber + 1, playerCharacter, worldState, storyMemory, inventory, combatEncounter, abilityState);
    }

    public GameState recordNarrativeTurn(String playerAction, String storyText) {
        if (!storyMemory.recentTurns().isEmpty() && storyMemory.recentTurns().getLast().turnNumber() >= turnNumber) {
            throw new IllegalStateException("현재 turn의 narrative가 이미 기록되어 있습니다.");
        }
        return new GameState(turnNumber, playerCharacter, worldState,
                storyMemory.append(new GameTurn(turnNumber, playerAction, storyText)), inventory, combatEncounter, abilityState);
    }

    public GameState advance(String playerAction, String storyText) {
        return advanceTurn().recordNarrativeTurn(playerAction, storyText);
    }
}
