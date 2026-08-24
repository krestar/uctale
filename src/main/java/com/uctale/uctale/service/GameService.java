package com.uctale.uctale.service;

import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.application.game.ImagePromptComposer;
import com.uctale.uctale.application.image.ImageGenerator;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final NarrativeGenerator narrativeGenerator;
    private final ImageGenerator imageGenerator;
    private final GamePersistenceService gamePersistenceService;
    private final ChoiceCodec choiceCodec;
    private final ImagePromptComposer imagePromptComposer;

    public GameResponse initGame(GameInitRequest request) {
        NarrativeTurn opening = narrativeGenerator.createOpening(request.worldSetting(), request.characterSetting());
        List<GameChoice> choices = toGameChoices(opening.choices());

        String imagePrompt = imagePromptComposer.compose(opening.visualAssets());
        if (imagePrompt == null || imagePrompt.isBlank()) {
            imagePrompt = "mysterious atmosphere, " + request.worldSetting();
        }
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
        NarrativeTurn nextTurn = narrativeGenerator.createNextTurn(
                loadedTurn.worldSetting(),
                loadedTurn.characterSetting(),
                loadedTurn.storyText(),
                userChoiceText
        );
        List<GameChoice> choices = toGameChoices(nextTurn.choices());

        String imageUrl = loadedTurn.imageUrl();
        String imagePrompt = imagePromptComposer.compose(nextTurn.visualAssets());
        if (imagePrompt != null && !imagePrompt.isBlank()) {
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
        if (choices == null) {
            return List.of();
        }
        return choices.stream()
                .map(choice -> new GameChoice(choice.id(), choice.text()))
                .toList();
    }
}
