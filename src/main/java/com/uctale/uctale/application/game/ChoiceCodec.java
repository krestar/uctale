package com.uctale.uctale.application.game;

import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.domain.action.ActionType;
import com.uctale.uctale.domain.action.AvailableAction;
import com.uctale.uctale.domain.action.PlayerAction;
import com.uctale.uctale.domain.game.StatType;
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

    static final StatType DEFAULT_SKILL_STAT = StatType.WILL;
    static final int DEFAULT_SKILL_DC = 10;
    static final int DEFAULT_SITUATIONAL_MODIFIER = 0;

    private final ObjectMapper objectMapper;

    public ChoiceCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<GameChoice> issue(List<NarrativeTurn.Choice> choices, int sourceTurn) {
        return choices.stream()
                .map(choice -> new AvailableAction(
                        choice.id(),
                        UUID.randomUUID().toString(),
                        ActionType.SKILL_CHECK,
                        sourceTurn,
                        Map.of(
                                "choiceId", Integer.toString(choice.id()),
                                "statType", DEFAULT_SKILL_STAT.name(),
                                "dc", Integer.toString(DEFAULT_SKILL_DC),
                                "situationalModifier", Integer.toString(DEFAULT_SITUATIONAL_MODIFIER)
                        ),
                        choice.text()
                ))
                .map(this::toDto)
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
        GameChoice storedChoice = deserialize(json).stream()
                .filter(choice -> choice.id() == request.choiceId())
                .findFirst()
                .orElseThrow(this::invalidAction);

        boolean legacyWireRequest = request.actionToken() == null
                && request.actionType() == null
                && request.sourceTurn() == null
                && request.arguments() == null;

        if (storedChoice.actionToken() == null || storedChoice.actionToken().isBlank()) {
            if (!legacyWireRequest) throw invalidAction();
            return legacyPlayerAction(storedChoice, request.expectedTurn());
        }

        AvailableAction available = toDomain(storedChoice);
        if (available.sourceTurn() != request.expectedTurn()) {
            throw invalidAction();
        }
        if (legacyWireRequest) {
            return legacyPlayerAction(storedChoice, request.expectedTurn());
        }

        ActionType requestedType;
        try {
            requestedType = ActionType.valueOf(request.actionType());
        } catch (RuntimeException exception) {
            throw invalidAction();
        }

        Map<String, String> requestedArguments;
        try {
            requestedArguments = request.arguments() == null ? Map.of() : Map.copyOf(request.arguments());
        } catch (RuntimeException exception) {
            throw invalidAction();
        }
        if (!available.token().equals(request.actionToken())
                || available.type() != requestedType
                || request.sourceTurn() == null
                || available.sourceTurn() != request.sourceTurn()
                || !available.arguments().equals(requestedArguments)) {
            throw invalidAction();
        }

        return playerActionFrom(available);
    }

    public String findText(String json, int choiceId) {
        return deserialize(json).stream()
                .filter(choice -> choice.id() == choiceId)
                .findFirst()
                .map(GameChoice::text)
                .orElseThrow(this::invalidAction);
    }

    private PlayerAction legacyPlayerAction(GameChoice storedChoice, int expectedTurn) {
        return new PlayerAction(
                storedChoice.id(),
                null,
                ActionType.NARRATIVE_CHOICE,
                expectedTurn,
                Map.of("choiceId", Integer.toString(storedChoice.id())),
                storedChoice.text()
        );
    }

    private PlayerAction playerActionFrom(AvailableAction available) {
        return new PlayerAction(
                available.legacyChoiceId(),
                available.token(),
                available.type(),
                available.sourceTurn(),
                available.arguments(),
                available.displayText()
        );
    }

    private AvailableAction toDomain(GameChoice choice) {
        try {
            return new AvailableAction(
                    choice.id(),
                    choice.actionToken(),
                    ActionType.valueOf(choice.actionType()),
                    choice.sourceTurn() == null ? 0 : choice.sourceTurn(),
                    choice.arguments(),
                    choice.text()
            );
        } catch (RuntimeException exception) {
            throw invalidAction();
        }
    }

    private GameChoice toDto(AvailableAction action) {
        return new GameChoice(
                action.legacyChoiceId(),
                action.displayText(),
                action.token(),
                action.type().name(),
                action.sourceTurn(),
                action.arguments()
        );
    }

    private InvalidChoiceException invalidAction() {
        return new InvalidChoiceException("현재 턴에서 서버가 허용한 행동이 아닙니다.");
    }
}
