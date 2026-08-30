package com.uctale.uctale.application.game;

import com.uctale.uctale.domain.game.GameState;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class GameStateCodec {

    private final ObjectMapper objectMapper;
    private final GameStateUpgrader gameStateUpgrader;

    public GameStateCodec(ObjectMapper objectMapper, GameStateUpgrader gameStateUpgrader) {
        this.objectMapper = objectMapper;
        this.gameStateUpgrader = gameStateUpgrader;
    }

    public String serialize(GameState state) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "schemaVersion", GameStateSnapshotFormat.CURRENT_SCHEMA_VERSION,
                    "rulesetVersion", GameStateSnapshotFormat.CURRENT_RULESET_VERSION,
                    "state", state
            ));
        } catch (JacksonException e) {
            throw new GameStateSnapshotException("GameState snapshot 직렬화에 실패했습니다.", e);
        }
    }

    public GameState deserialize(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            GameStateUpgrader.UpgradedSnapshot upgraded = gameStateUpgrader.upgrade(root);
            return objectMapper.readValue(upgraded.state().toString(), GameState.class);
        } catch (GameStateSnapshotException e) {
            throw e;
        } catch (JacksonException | IllegalArgumentException e) {
            throw new GameStateSnapshotException("GameState snapshot 역직렬화에 실패했습니다.", e);
        }
    }
}
