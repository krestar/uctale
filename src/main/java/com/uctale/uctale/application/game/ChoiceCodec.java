package com.uctale.uctale.application.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.dto.GameChoice;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChoiceCodec {

    private final ObjectMapper objectMapper;

    public ChoiceCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(List<GameChoice> choices) {
        try {
            return objectMapper.writeValueAsString(choices);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("선택지 JSON 변환에 실패했습니다.", e);
        }
    }

    public String findText(String json, int choiceId) {
        final List<GameChoice> choices;
        try {
            choices = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 선택지를 읽을 수 없습니다.", e);
        }

        return choices.stream()
                .filter(choice -> choice.id() == choiceId)
                .findFirst()
                .map(GameChoice::text)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 선택지입니다."));
    }
}
