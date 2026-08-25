package com.uctale.uctale.service;

import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.image.ImageGenerator;
import com.uctale.uctale.application.narrative.InvalidNarrativeResponseException;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_STORY_LENGTH = 50_000;
    private static final int MAX_CHOICE_TEXT_LENGTH = 255;
    private static final int MAX_CHOICES = 8;
    private static final int MAX_IMAGE_PROMPT_LENGTH = 2_000;

    private final NarrativeGenerator narrativeGenerator;
    private final ImageGenerator imageGenerator;
    private final GamePersistenceService gamePersistenceService;
    private final ChoiceCodec choiceCodec;
    private final ImagePromptComposer imagePromptComposer;

    public GameResponse initGame(GameInitRequest request) {
        NarrativeTurn opening = narrativeGenerator.createOpening(request.worldSetting(), request.characterSetting());
        validateNarrativeTurn(opening);
        List<GameChoice> choices = toGameChoices(opening.choices());

        String imagePrompt = imagePromptComposer.compose(opening.visualAssets());
        if (imagePrompt == null || imagePrompt.isBlank()) {
            imagePrompt = "mysterious atmosphere, " + request.worldSetting();
        }
        validateImagePrompt(imagePrompt);
        String imageUrl = imageGenerator.createPublicUrl(imagePrompt, "16:9");

        var session = gamePersistenceService.saveOpening(
                request.worldSetting(),
                request.characterSetting(),
                opening.storyText(),
                choiceCodec.serialize(choices),
                imageUrl
        );

        return toResponse(session.getId(), session.getCurrentTurn(), opening, choices, imageUrl);
    }

    public GameResponse progressGame(GameProgressRequest request) {
        GamePersistenceService.LoadedTurn loadedTurn = gamePersistenceService.loadLatestTurn(
                request.sessionId(),
                request.expectedTurn()
        );

        String userChoiceText = choiceCodec.findText(loadedTurn.choicesJson(), request.choiceId());
        NarrativeContext narrativeContext = NarrativeContext.from(loadedTurn.gameState(), userChoiceText);
        NarrativeTurn nextTurn = narrativeGenerator.createNextTurn(narrativeContext);
        validateNarrativeTurn(nextTurn);
        List<GameChoice> choices = toGameChoices(nextTurn.choices());

        String imageUrl = loadedTurn.imageUrl();
        String imagePrompt = imagePromptComposer.compose(nextTurn.visualAssets());
        if (imagePrompt != null && !imagePrompt.isBlank()) {
            validateImagePrompt(imagePrompt);
            log.info("새로운 이미지 생성 요청");
            String newImageUrl = imageGenerator.createPublicUrl(imagePrompt, "16:9");
            if (newImageUrl != null) {
                imageUrl = newImageUrl;
            }
        } else {
            log.info("시각적 변화 없음 -> 이전 이미지 재사용");
        }

        int savedTurn = gamePersistenceService.saveNextTurn(
                loadedTurn.sessionId(),
                request.expectedTurn(),
                userChoiceText,
                nextTurn.storyText(),
                choiceCodec.serialize(choices),
                imageUrl
        );

        return toResponse(loadedTurn.sessionId(), savedTurn, nextTurn, choices, imageUrl);
    }

    private void validateNarrativeTurn(NarrativeTurn turn) {
        if (turn == null) {
            throw new InvalidNarrativeResponseException("Narrative 응답이 없습니다.");
        }
        if (turn.title() == null || turn.title().isBlank() || turn.title().length() > MAX_TITLE_LENGTH) {
            throw new InvalidNarrativeResponseException("Narrative 제목이 올바르지 않습니다.");
        }
        if (turn.storyText() == null || turn.storyText().isBlank() || turn.storyText().length() > MAX_STORY_LENGTH) {
            throw new InvalidNarrativeResponseException("Narrative 본문이 올바르지 않습니다.");
        }
        if (turn.choices() == null || turn.choices().isEmpty() || turn.choices().size() > MAX_CHOICES) {
            throw new InvalidNarrativeResponseException("Narrative 선택지 수가 올바르지 않습니다.");
        }

        Set<Integer> choiceIds = new HashSet<>();
        for (NarrativeTurn.Choice choice : turn.choices()) {
            if (choice == null || choice.id() <= 0 || !choiceIds.add(choice.id())) {
                throw new InvalidNarrativeResponseException("Narrative 선택지 ID가 올바르지 않습니다.");
            }
            if (choice.text() == null || choice.text().isBlank() || choice.text().length() > MAX_CHOICE_TEXT_LENGTH) {
                throw new InvalidNarrativeResponseException("Narrative 선택지 문구가 올바르지 않습니다.");
            }
        }
    }

    private void validateImagePrompt(String imagePrompt) {
        if (imagePrompt != null && imagePrompt.length() > MAX_IMAGE_PROMPT_LENGTH) {
            throw new InvalidNarrativeResponseException("이미지 prompt가 너무 깁니다.");
        }
    }

    private GameResponse toResponse(
            Long sessionId,
            int turnNumber,
            NarrativeTurn turn,
            List<GameChoice> choices,
            String imageUrl
    ) {
        return new GameResponse(sessionId, turnNumber, turn.title(), turn.storyText(), choices, imageUrl);
    }

    private List<GameChoice> toGameChoices(List<NarrativeTurn.Choice> choices) {
        return choices.stream()
                .map(choice -> new GameChoice(choice.id(), choice.text()))
                .toList();
    }
}
