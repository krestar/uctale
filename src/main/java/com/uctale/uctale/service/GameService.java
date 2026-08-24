package com.uctale.uctale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.dto.GeminiResponse;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GameService {

    private final GeminiService geminiService;
    private final NanoBananaService nanoBananaService;
    private final GameSessionRepository gameSessionRepository;
    private final GameLogRepository gameLogRepository;
    private final ObjectMapper objectMapper;

    public GameResponse initGame(GameInitRequest request) {
        GeminiResponse geminiResponse = geminiService.getOpening(request);

        String imagePrompt = determineImagePrompt(geminiResponse.visual_assets());
        if (imagePrompt == null || imagePrompt.isBlank()) {
            imagePrompt = "mysterious atmosphere, " + request.worldSetting();
        }

        String imageUrl = nanoBananaService.generateImage(imagePrompt, "16:9");

        GameSession session = new GameSession(request.worldSetting(), request.characterSetting());
        gameSessionRepository.save(session);

        String choicesJson = convertChoicesToJson(geminiResponse.choices());
        GameLog log = new GameLog(session, 1, geminiResponse.story_text(), choicesJson, imageUrl);
        gameLogRepository.save(log);

        return new GameResponse(
                session.getId(),
                geminiResponse.title(),
                geminiResponse.story_text(),
                geminiResponse.choices(),
                imageUrl,
                null
        );
    }

    public GameResponse progressGame(GameProgressRequest request) {
        GameSession session = gameSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다."));

        GameLog lastLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)
                .orElseThrow(() -> new IllegalStateException("게임 로그가 없습니다."));

        String userChoiceText = findChoiceText(lastLog.getChoicesJson(), request.choiceId());
        lastLog.updateUserChoice(userChoiceText);

        GeminiResponse nextTurnResponse = geminiService.getNextTurn(
                session.getWorldSetting(),
                session.getCharacterSetting(),
                lastLog.getStoryText(),
                userChoiceText
        );

        String imageUrl = lastLog.getImageUrl();
        String newPrompt = determineImagePrompt(nextTurnResponse.visual_assets());

        if (newPrompt != null && !newPrompt.isBlank()) {
            log.info("새로운 이미지 생성 요청");
            String newImage = nanoBananaService.generateImage(newPrompt, "16:9");
            if (newImage != null) {
                imageUrl = newImage;
            }
        } else {
            log.info("시각적 변화 없음 -> 이전 이미지 재사용");
        }

        String choicesJson = convertChoicesToJson(nextTurnResponse.choices());
        GameLog newLog = new GameLog(session, lastLog.getTurnNumber() + 1, nextTurnResponse.story_text(), choicesJson, imageUrl);
        gameLogRepository.save(newLog);

        return new GameResponse(
                session.getId(),
                nextTurnResponse.title(),
                nextTurnResponse.story_text(),
                nextTurnResponse.choices(),
                imageUrl,
                null
        );
    }

    private String determineImagePrompt(GeminiResponse.VisualAssets assets) {
        if (assets == null) return null;

        List<String> prompts = new ArrayList<>();

        if (assets.characters() != null && !assets.characters().isEmpty()) {
            prompts.addAll(assets.characters());
        }

        if (assets.assets() != null && !assets.assets().isEmpty()) {
            prompts.addAll(assets.assets());
        }

        if (assets.background() != null && !assets.background().isBlank()) {
            prompts.add(assets.background());
        }

        if (prompts.isEmpty()) {
            return null;
        }

        return String.join(", ", prompts);
    }

    private String convertChoicesToJson(List<GeminiResponse.Choice> choices) {
        try {
            return objectMapper.writeValueAsString(choices);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("선택지 JSON 변환에 실패했습니다.", e);
        }
    }

    private String findChoiceText(String json, int choiceId) {
        final List<GeminiResponse.Choice> choices;
        try {
            choices = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 선택지를 읽을 수 없습니다.", e);
        }

        return choices.stream()
                .filter(choice -> choice.id() == choiceId)
                .findFirst()
                .map(GeminiResponse.Choice::text)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 선택지입니다."));
    }
}