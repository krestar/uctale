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
        // 1. Gemini에게 오프닝 스토리 요청
        GeminiResponse geminiResponse = geminiService.getOpening(request);

        // 2. 이미지 생성 (배경)
        // 오프닝은 무조건 이미지가 필요하므로 생성 요청
        String bgPrompt = geminiResponse.visual_assets().background();
        String imageUrl = nanoBananaService.generateImage(bgPrompt, "16:9");

        // 3. DB 저장 (세션 + 첫 번째 로그)
        GameSession session = new GameSession(request.worldSetting(), request.characterSetting());
        gameSessionRepository.save(session);

        // 선택지 목록을 JSON 문자열로 변환하여 저장
        String choicesJson = convertChoicesToJson(geminiResponse.choices());

        GameLog log = new GameLog(session, 1, geminiResponse.story_text(), choicesJson, imageUrl);
        gameLogRepository.save(log);

        // 4. 응답 반환
        return new GameResponse(
                geminiResponse.title(),
                geminiResponse.story_text(),
                geminiResponse.choices(),
                imageUrl,
                session.getId().toString() // 세션 ID 반환 (String으로 변환)
        );
    }

    // 게임 진행 (다음 턴)
    public GameResponse progressGame(GameProgressRequest request) {
        // 1. 세션 및 이전 로그 조회
        GameSession session = gameSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 세션입니다."));

        GameLog lastLog = gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)
                .orElseThrow(() -> new IllegalStateException("게임 로그가 없습니다."));

        // 2. 사용자가 선택한 문구 찾기 (1번 -> "문을 연다")
        String userChoiceText = findChoiceText(lastLog.getChoicesJson(), request.choiceId());

        // 이전 로그에 사용자 선택 업데이트
        lastLog.updateUserChoice(userChoiceText);

        // 3. Gemini에게 다음 이야기 요청
        GeminiResponse nextTurnResponse = geminiService.getNextTurn(
                session.getWorldSetting(),
                session.getCharacterSetting(),
                lastLog.getStoryText(), // 이전 스토리
                userChoiceText          // 유저의 행동
        );

        // 4. 이미지 생성 판단 (자원 절약 로직)
        String imageUrl = lastLog.getImageUrl(); // 기본값: 이전 이미지 재사용
        String newBgPrompt = nextTurnResponse.visual_assets().background();

        // Gemini가 새로운 배경 프롬프트를 줬다면 -> 새로 생성
        if (newBgPrompt != null && !newBgPrompt.isBlank()) {
            log.info("새로운 배경 생성 요청: {}", newBgPrompt);
            String newImage = nanoBananaService.generateImage(newBgPrompt, "16:9");
            if (newImage != null) {
                imageUrl = newImage;
            }
        } else {
            log.info("이전 배경 재사용");
        }

        // 5. 새로운 로그 저장
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

    // Helper: 선택지 리스트 -> JSON String 변환
    private String convertChoicesToJson(List<GeminiResponse.Choice> choices) {
        try {
            return objectMapper.writeValueAsString(choices);
        } catch (JsonProcessingException e) {
            log.error("선택지 JSON 변환 실패", e);
            return "[]";
        }
    }

    // JSON 문자열에서 ID에 해당하는 텍스트 추출
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