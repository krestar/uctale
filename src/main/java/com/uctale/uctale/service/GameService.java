package com.uctale.uctale.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.dto.GameInitRequest;
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
        // (추후 세션 ID도 반환해야 클라이언트가 다음 요청 때 보낼 수 있음)
        return new GameResponse(
                geminiResponse.story_text(),
                geminiResponse.choices(),
                imageUrl,
                null // 캐릭터 이미지는 필요 시 추가
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
}