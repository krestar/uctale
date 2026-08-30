package com.uctale.uctale.application.game;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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
        } catch (JacksonException e) {
            throw new IllegalStateException("선택지 JSON 변환에 실패했습니다.", e);
        }
    }

    public List<GameChoice> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 선택지를 읽을 수 없습니다.", e);
        }
    }

    public String findText(String json, int choiceId) {
        return deserialize(json).stream()
                .filter(choice -> choice.id() == choiceId)
                .findFirst()
                .map(GameChoice::text)
                .orElseThrow(() -> new InvalidChoiceException("현재 턴에서 선택할 수 없는 선택지입니다."));
    }
}
