package com.uctale.uctale.application.game;

import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameProgressRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ChoiceCodec {

    private final ObjectMapper objectMapper;

    public ChoiceCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<GameChoice> issue(List<NarrativeTurn.Choice> choices, int sourceTurn) {
        return choices.stream()
                .map(choice -> new GameChoice(
                        choice.id(),
                        choice.text(),
                        UUID.randomUUID().toString(),
                        ActionType.NARRATIVE_CHOICE.name(),
                        sourceTurn,
                        Map.of("choiceId", Integer.toString(choice.id()))
                ))
                .toList();
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

    public PlayerAction resolve(String json, GameProgressRequest request) {
        GameChoice available = deserialize(json).stream()
                .filter(choice -> choice.id() == request.choiceId())
                .findFirst()
                .orElseThrow(() -> invalidAction());

        if (available.actionToken() == null || available.actionToken().isBlank()) {
            if (request.actionToken() != null || request.actionType() != null || request.sourceTurn() != null || request.arguments() != null) {
                throw invalidAction();
            }
            return new PlayerAction(
                    available.id(),
                    null,
                    ActionType.NARRATIVE_CHOICE,
                    request.expectedTurn(),
                    Map.of("choiceId", Integer.toString(available.id())),
                    available.text()
            );
        }

        ActionType requestedType;
        try {
            requestedType = ActionType.valueOf(request.actionType());
        } catch (RuntimeException exception) {
            throw invalidAction();
        }

        Map<String, String> requestedArguments = request.arguments() == null ? Map.of() : Map.copyOf(request.arguments());
        Map<String, String> availableArguments = available.arguments() == null ? Map.of() : Map.copyOf(available.arguments());
        if (!available.actionToken().equals(request.actionToken())
                || !available.actionType().equals(requestedType.name())
                || available.sourceTurn() == null
                || available.sourceTurn() != request.expectedTurn()
                || !available.sourceTurn().equals(request.sourceTurn())
                || !availableArguments.equals(requestedArguments)) {
            throw invalidAction();
        }

        return new PlayerAction(
                available.id(),
                available.actionToken(),
                requestedType,
                available.sourceTurn(),
                availableArguments,
                available.text()
        );
    }

    public String findText(String json, int choiceId) {
        return deserialize(json).stream()
                .filter(choice -> choice.id() == choiceId)
                .findFirst()
                .map(GameChoice::text)
                .orElseThrow(() -> invalidAction());
    }

    private InvalidChoiceException invalidAction() {
        return new InvalidChoiceException("현재 턴에서 서버가 허용한 행동이 아닙니다.");
    }
}
