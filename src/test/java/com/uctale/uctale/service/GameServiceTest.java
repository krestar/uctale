package com.uctale.uctale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.domain.GameLog;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.dto.GeminiResponse;
import com.uctale.uctale.repository.GameLogRepository;
import com.uctale.uctale.repository.GameSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GeminiService geminiService;

    @Mock
    private NanoBananaService nanoBananaService;

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private GameLogRepository gameLogRepository;

    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameService = new GameService(
                geminiService,
                nanoBananaService,
                gameSessionRepository,
                gameLogRepository,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("게임 초기화는 명시적인 세션 ID와 첫 로그를 반환한다")
    void initGame_ReturnsExplicitSessionId() {
        GameInitRequest request = new GameInitRequest("좀비 아포칼립스", "김대리");
        GeminiResponse opening = new GeminiResponse(
                "첫날 밤",
                "오프닝 스토리",
                List.of(new GeminiResponse.Choice(1, "도망간다")),
                new GeminiResponse.VisualAssets("dark street", List.of("zombie"), List.of())
        );

        given(geminiService.getOpening(request)).willReturn(opening);
        given(nanoBananaService.generateImage(any(), any())).willReturn("/api/game/image?prompt=test&aspectRatio=16%3A9");
        given(gameSessionRepository.save(any(GameSession.class))).willAnswer(invocation -> {
            GameSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 42L);
            return session;
        });

        GameResponse response = gameService.initGame(request);

        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.title()).isEqualTo("첫날 밤");
        assertThat(response.storyText()).isEqualTo("오프닝 스토리");
        assertThat(response.mainImageUrl()).startsWith("/api/game/image?");
        assertThat(response.choices()).extracting(GeminiResponse.Choice::text).containsExactly("도망간다");
        verify(gameSessionRepository).save(any(GameSession.class));
        verify(gameLogRepository).save(any(GameLog.class));
    }

    @Test
    @DisplayName("게임 진행은 저장된 선택지 문구와 직전 스토리를 다음 Gemini 요청에 사용한다")
    void progressGame_UsesPreviousStoryAndSelectedChoice() throws Exception {
        GameSession session = new GameSession("좀비 아포칼립스", "김대리");
        ReflectionTestUtils.setField(session, "id", 42L);

        String choicesJson = new ObjectMapper().writeValueAsString(
                List.of(new GeminiResponse.Choice(1, "문을 잠근다"))
        );
        GameLog lastLog = new GameLog(session, 1, "직전 스토리", choicesJson, "/api/game/image?prompt=old&aspectRatio=16%3A9");

        GeminiResponse nextTurn = new GeminiResponse(
                "다음 장면",
                "다음 스토리",
                List.of(new GeminiResponse.Choice(1, "기다린다")),
                new GeminiResponse.VisualAssets(null, List.of(), List.of())
        );

        given(gameSessionRepository.findById(42L)).willReturn(Optional.of(session));
        given(gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)).willReturn(Optional.of(lastLog));
        given(geminiService.getNextTurn("좀비 아포칼립스", "김대리", "직전 스토리", "문을 잠근다"))
                .willReturn(nextTurn);

        GameResponse response = gameService.progressGame(new GameProgressRequest(42L, 1));

        assertThat(lastLog.getUserChoice()).isEqualTo("문을 잠근다");
        assertThat(response.sessionId()).isEqualTo(42L);
        assertThat(response.storyText()).isEqualTo("다음 스토리");
        assertThat(response.mainImageUrl()).isEqualTo("/api/game/image?prompt=old&aspectRatio=16%3A9");
        verify(geminiService).getNextTurn("좀비 아포칼립스", "김대리", "직전 스토리", "문을 잠근다");
        verify(gameLogRepository).save(any(GameLog.class));
    }

    @Test
    @DisplayName("존재하지 않는 선택지는 다음 스토리를 생성하지 않고 실패한다")
    void progressGame_RejectsUnknownChoice() throws Exception {
        GameSession session = new GameSession("좀비 아포칼립스", "김대리");
        ReflectionTestUtils.setField(session, "id", 42L);
        String choicesJson = new ObjectMapper().writeValueAsString(
                List.of(new GeminiResponse.Choice(1, "문을 잠근다"))
        );
        GameLog lastLog = new GameLog(session, 1, "직전 스토리", choicesJson, null);

        given(gameSessionRepository.findById(42L)).willReturn(Optional.of(session));
        given(gameLogRepository.findTopByGameSessionOrderByTurnNumberDesc(session)).willReturn(Optional.of(lastLog));

        assertThatThrownBy(() -> gameService.progressGame(new GameProgressRequest(42L, 999)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 선택지입니다.");

        verify(geminiService, never()).getNextTurn(any(), any(), any(), any());
        verify(gameLogRepository, never()).save(any(GameLog.class));
    }
}