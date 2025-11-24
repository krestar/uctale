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

    /**
     * 게임 초기화 및 오프닝 생성
     */
    public GameResponse initGame(GameInitRequest request) {
        GeminiResponse geminiResponse = geminiService.getOpening(request);

        // 오프닝은 무조건 이미지를 생성하도록 유도 (없으면 기본값 사용)
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
                geminiResponse.title(),
                geminiResponse.story_text(),
                geminiResponse.choices(),
                imageUrl,
                session.getId().toString()
        );
    }

    // 게임 진행 (다음 턴)
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

        // [핵심] 이미지 생성 판단 로직
        String imageUrl = lastLog.getImageUrl(); // 기본값: 이전 이미지 유지
        String newPrompt = determineImagePrompt(nextTurnResponse.visual_assets());

        // 새로운 프롬프트가 '존재할 때만' 생성 (null이면 이전 이미지 재사용)
        if (newPrompt != null && !newPrompt.isBlank()) {
            log.info("새로운 이미지 생성 요청: {}", newPrompt);
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
                nextTurnResponse.title(),
                nextTurnResponse.story_text(),
                nextTurnResponse.choices(),
                imageUrl,
                session.getId().toString()
        );
    }

    // [수정] 프롬프트 결정 헬퍼 메서드: '선택'이 아닌 '조합'으로 변경
    private String determineImagePrompt(GeminiResponse.VisualAssets assets) {
        if (assets == null) return null;

        List<String> prompts = new ArrayList<>();

        // 1. 캐릭터 (적/NPC)
        if (assets.characters() != null && !assets.characters().isEmpty()) {
            prompts.addAll(assets.characters());
        }

        // 2. 아이템/사물 (상호작용)
        if (assets.assets() != null && !assets.assets().isEmpty()) {
            prompts.addAll(assets.assets());
        }

        // 3. 배경 (장소)
        if (assets.background() != null && !assets.background().isBlank()) {
            prompts.add(assets.background());
        }

        // 모든 요소가 비어있으면 null 반환 (이미지 생성 안 함)
        if (prompts.isEmpty()) {
            return null;
        }

        // 요소들을 콤마로 연결하여 하나의 풍성한 프롬프트 생성
        // 예: "bloody zombie, red fire extinguisher, dark subway station"
        // AI 화가(Flux)가 이 조합을 바탕으로 '지하철에서 소화기가 있는 좀비 씬'을 그려줌
        return String.join(", ", prompts);
    }

    private String convertChoicesToJson(List<GeminiResponse.Choice> choices) {
        try {
            return objectMapper.writeValueAsString(choices);
        } catch (JsonProcessingException e) {
            log.error("선택지 JSON 변환 실패", e);
            return "[]";
        }
    }

    private String findChoiceText(String json, int choiceId) {
        try {
            List<GeminiResponse.Choice> choices = objectMapper.readValue(json, new TypeReference<>() {});
            return choices.stream()
                    .filter(c -> c.id() == choiceId)
                    .findFirst()
                    .map(GeminiResponse.Choice::text)
                    .orElse("알 수 없는 행동");
        } catch (Exception e) {
            return "선택지 처리 중 오류";
        }
    }
}