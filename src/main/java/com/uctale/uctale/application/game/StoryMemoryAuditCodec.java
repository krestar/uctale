package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.CanonicalFact;
import com.uctale.uctale.domain.game.GameTurn;
import com.uctale.uctale.domain.game.StoryMemory;
import com.uctale.uctale.domain.game.StorySummary;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class StoryMemoryAuditCodec {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public StoryMemoryAuditCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(StoryMemory memory) {
        if (memory == null) throw new IllegalArgumentException("StoryMemory audit 대상은 필수입니다.");
        try {
            return objectMapper.writeValueAsString(new AuditEnvelope(CURRENT_SCHEMA_VERSION, memory));
        } catch (JacksonException exception) {
            throw new IllegalStateException("StoryMemory GameLog audit 직렬화에 실패했습니다.", exception);
        }
    }

    public StoryMemory deserialize(String json, int stateVersion) {
        if (json == null) return null;
        if (json.isBlank()) throw new IllegalStateException("StoryMemory GameLog audit가 비어 있습니다.");
        try {
            JsonNode root = objectMapper.readTree(json);
            validateEnvelope(root);
            int schemaVersion = root.get("schemaVersion").asInt();
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("지원하지 않는 StoryMemory audit schemaVersion입니다: " + schemaVersion);
            }
            JsonNode memoryNode = root.get("storyMemory");
            validateShape(memoryNode);
            StoryMemory memory = objectMapper.treeToValue(memoryNode, StoryMemory.class);
            validateSemantics(memory, stateVersion);
            return memory;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("StoryMemory GameLog audit 역직렬화에 실패했습니다.", exception);
        }
    }

    private void validateEnvelope(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("StoryMemory audit envelope은 JSON object여야 합니다.");
        }
        JsonNode schemaVersion = root.get("schemaVersion");
        JsonNode storyMemory = root.get("storyMemory");
        if (schemaVersion == null || !schemaVersion.isIntegralNumber()
                || storyMemory == null || !storyMemory.isObject()) {
            throw new IllegalArgumentException("StoryMemory audit envelope 필수 필드가 누락되었습니다.");
        }
    }

    private void validateShape(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("StoryMemory audit는 JSON object여야 합니다.");
        }
        JsonNode facts = root.get("canonicalFacts");
        JsonNode summary = root.get("rollingSummary");
        JsonNode recentTurns = root.get("recentTurns");
        if (facts == null || !facts.isArray() || summary == null || !summary.isObject()
                || recentTurns == null || !recentTurns.isArray()) {
            throw new IllegalArgumentException("StoryMemory audit 필수 필드가 누락되었습니다.");
        }
        for (JsonNode fact : facts) {
            if (!fact.isObject() || !textual(fact, "key") || !textual(fact, "value")
                    || !integral(fact, "sourceTurn") || !textual(fact, "status")) {
                throw new IllegalArgumentException("StoryMemory canonical fact audit가 손상되었습니다.");
            }
        }
        if (!integral(summary, "sourceFromTurn") || !integral(summary, "sourceToTurn")
                || !integral(summary, "stateVersion") || !textual(summary, "text")) {
            throw new IllegalArgumentException("StoryMemory rolling summary audit가 손상되었습니다.");
        }
        for (JsonNode turn : recentTurns) {
            if (!turn.isObject() || !integral(turn, "turnNumber") || !textual(turn, "playerAction")
                    || !textual(turn, "storyText")) {
                throw new IllegalArgumentException("StoryMemory recent turn audit가 손상되었습니다.");
            }
        }
    }

    private void validateSemantics(StoryMemory memory, int stateVersion) {
        if (stateVersion < 1) throw new IllegalArgumentException("StoryMemory audit stateVersion이 올바르지 않습니다.");

        StorySummary summary = memory.rollingSummary();
        if (!summary.emptySummary()
                && (summary.stateVersion() > stateVersion || summary.sourceToTurn() > stateVersion)) {
            throw new IllegalArgumentException("StoryMemory summary가 GameLog state version보다 미래를 가리킵니다.");
        }
        for (CanonicalFact fact : memory.canonicalFacts()) {
            if (fact.sourceTurn() > stateVersion) {
                throw new IllegalArgumentException("StoryMemory canonical fact가 GameLog state version보다 미래를 가리킵니다.");
            }
        }

        int previousTurn = 0;
        for (GameTurn turn : memory.recentTurns()) {
            if (turn.turnNumber() <= previousTurn || turn.turnNumber() > stateVersion) {
                throw new IllegalArgumentException("StoryMemory recent turn 순서가 GameLog state version과 일치하지 않습니다.");
            }
            if (!summary.emptySummary() && turn.turnNumber() <= summary.sourceToTurn()) {
                throw new IllegalArgumentException("StoryMemory summary와 recent turn source range가 겹칩니다.");
            }
            previousTurn = turn.turnNumber();
        }
        if (memory.recentTurns().isEmpty()
                || memory.recentTurns().getLast().turnNumber() != stateVersion) {
            throw new IllegalArgumentException("StoryMemory recent turn은 현재 GameLog state version까지 포함해야 합니다.");
        }
    }

    private record AuditEnvelope(int schemaVersion, StoryMemory storyMemory) {}

    private boolean textual(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual();
    }

    private boolean integral(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isIntegralNumber();
    }
}
