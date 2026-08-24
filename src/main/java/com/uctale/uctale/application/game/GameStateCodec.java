package com.uctale.uctale.application.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.domain.game.GameState;
import org.springframework.stereotype.Component;

@Component
public class GameStateCodec {

    private final ObjectMapper objectMapper;

    public GameStateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(GameState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GameState 직렬화에 실패했습니다.", e);
        }
    }

    public GameState deserialize(String json) {
        try {
            return objectMapper.readValue(json, GameState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("GameState 역직렬화에 실패했습니다.", e);
        }
    }
}
