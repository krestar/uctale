package com.uctale.uctale.domain.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RelationshipRules {

    public Result apply(RelationshipState state, List<RelationshipCommand> commands) {
        Objects.requireNonNull(state, "RelationshipState는 필수입니다.");
        if (commands == null || commands.isEmpty()) return new Result(state, List.of());
        RelationshipState current = state;
        List<GameResult.StateChange> changes = new ArrayList<>();
        for (RelationshipCommand command : commands) {
            Objects.requireNonNull(command, "relationship command는 null일 수 없습니다.");
            NpcRelationship existing = current.find(command.npc().instanceId());
            if (existing != null && !existing.npc().definitionId().equals(command.npc().definitionId())) {
                throw new IllegalArgumentException("NPC instanceId의 definitionId가 canonical state와 일치하지 않습니다.");
            }
            NpcRelationship base = existing == null ? NpcRelationship.neutral(command.npc()) : existing;
            if (base.alreadyApplied(command.sourceTurn(), command.sourceKey())) {
                base.validateRetry(command);
                continue;
            }
            NpcRelationship next = base.applyDelta(command.delta(), command.reason(), command.sourceTurn(), command.sourceKey());
            if (next.equals(base)) continue;
            current = current.put(next);
            changes.add(new GameResult.RelationshipChanged(existing, next, command.reason(), command.sourceTurn(), command.sourceKey()));
        }
        return new Result(current, changes);
    }

    public TurnResolution apply(TurnResolution resolution, List<RelationshipCommand> commands) {
        Objects.requireNonNull(resolution, "TurnResolution은 필수입니다.");
        int canonicalSourceTurn = resolution.stateTransition().previousState().turnNumber();
        if (commands != null) {
            for (RelationshipCommand command : commands) {
                Objects.requireNonNull(command, "relationship command는 null일 수 없습니다.");
                if (command.sourceTurn() != canonicalSourceTurn) {
                    throw new IllegalArgumentException("relationship source turn이 canonical turn과 일치하지 않습니다.");
                }
            }
        }
        GameState baseNext = resolution.stateTransition().nextState();
        Result relationship = apply(baseNext.relationshipState(), commands);
        if (relationship.stateChanges().isEmpty()) return resolution;

        List<GameResult.StateChange> changes = new ArrayList<>(resolution.gameResult().stateChanges());
        int turnIndex = -1;
        for (int i = 0; i < changes.size(); i++) {
            if (changes.get(i) instanceof GameResult.TurnAdvanced) { turnIndex = i; break; }
        }
        if (turnIndex < 0) throw new IllegalStateException("TurnResolution에 TURN_ADVANCED audit이 없습니다.");
        changes.addAll(turnIndex, relationship.stateChanges());

        List<GameResult.GameEvent> events = new ArrayList<>(resolution.gameResult().events());
        if (!events.contains(GameResult.GameEvent.RELATIONSHIP_CHANGED)) events.add(GameResult.GameEvent.RELATIONSHIP_CHANGED);
        GameResult original = resolution.gameResult();
        GameResult decorated = new GameResult(original.resolvedAction(), original.outcome(), original.skillCheckResult(),
                original.attackResult(), original.canonicalFacts(), events, changes, original.narrativeCues());
        GameState next = baseNext.withRelationshipState(relationship.relationshipState());
        return new TurnResolution(decorated, new StateTransition(resolution.stateTransition().previousState(), next));
    }

    public static RelationshipState replay(RelationshipState previous, List<GameResult.StateChange> changes) {
        Objects.requireNonNull(previous, "previous RelationshipState는 필수입니다.");
        RelationshipState state = previous;
        if (changes == null) return state;
        for (GameResult.StateChange change : changes) {
            if (!(change instanceof GameResult.RelationshipChanged relationship)) continue;
            String instanceId = relationship.nextValue().npc().instanceId();
            NpcRelationship actual = state.find(instanceId);
            if (!Objects.equals(actual, relationship.previousValue())) {
                throw new IllegalArgumentException("relationship audit previous 값이 canonical state와 일치하지 않습니다.");
            }
            state = state.put(relationship.nextValue());
        }
        return state;
    }

    public record Result(RelationshipState relationshipState, List<GameResult.StateChange> stateChanges) {
        public Result {
            Objects.requireNonNull(relationshipState, "relationshipState는 필수입니다.");
            stateChanges = stateChanges == null ? List.of() : List.copyOf(stateChanges);
        }
    }
}
