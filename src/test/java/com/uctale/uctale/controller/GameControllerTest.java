package com.uctale.uctale.controller;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.game.GameSessionNotFoundException;
import com.uctale.uctale.application.game.InvalidChoiceException;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private GameService gameService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GameController(gameService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("게임 초기화 응답은 첫 번째 턴을 제공하고 owner key를 서비스에 전달한다")
    void initGame_ReturnsFirstTurn() throws Exception {
        GameResponse response = new GameResponse(
                42L,
                1,
                "첫날 밤",
                "오프닝 스토리입니다.",
                List.of(new GameChoice(1, "도망간다")),
                "/api/game/image?prompt=test&aspectRatio=16%3A9"
        );
        given(gameService.initGame(eq(OWNER_KEY), any(GameInitRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/init")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameInitRequest("좀비 아포칼립스", "김대리"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(42))
                .andExpect(jsonPath("$.turnNumber").value(1));

        verify(gameService).initGame(eq(OWNER_KEY), any(GameInitRequest.class));
    }

    @Test
    @DisplayName("게임 진행 요청은 owner key와 기대 턴을 함께 서비스에 전달한다")
    void progressGame_Success() throws Exception {
        GameProgressRequest request = new GameProgressRequest(42L, 1, 1);
        GameResponse response = new GameResponse(
                42L,
                2,
                "두 번째 장면",
                "다음 턴 스토리입니다.",
                List.of(new GameChoice(2, "숨는다")),
                "/api/game/image?prompt=turn2&aspectRatio=16%3A9"
        );
        given(gameService.progressGame(eq(OWNER_KEY), any(GameProgressRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnNumber").value(2));
    }

    @Test
    @DisplayName("다른 owner의 세션은 존재하지 않는 세션과 동일한 404로 반환한다")
    void progressGame_MapsOwnershipMismatchToNotFound() throws Exception {
        given(gameService.progressGame(eq(OWNER_KEY), any(GameProgressRequest.class)))
                .willThrow(new GameSessionNotFoundException("존재하지 않는 세션입니다."));

        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 1, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("오래된 턴 요청은 안정적인 409 오류 코드로 반환한다")
    void progressGame_MapsTurnConflict() throws Exception {
        given(gameService.progressGame(eq(OWNER_KEY), any(GameProgressRequest.class)))
                .willThrow(new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다."));

        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 1, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TURN_CONFLICT"))
                .andExpect(jsonPath("$.message").value("이미 처리되었거나 오래된 턴 요청입니다."));
    }

    @Test
    @DisplayName("현재 턴에 없는 선택지는 422 오류 코드로 반환한다")
    void progressGame_MapsInvalidChoice() throws Exception {
        given(gameService.progressGame(eq(OWNER_KEY), any(GameProgressRequest.class)))
                .willThrow(new InvalidChoiceException("현재 턴에서 선택할 수 없는 선택지입니다."));

        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 99, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CHOICE"));
    }

    @Test
    @DisplayName("빈 세계관 설정은 provider 호출 전에 400으로 거부한다")
    void initGame_RejectsBlankWorldSetting() throws Exception {
        mockMvc.perform(post("/api/game/init")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameInitRequest("", "김대리"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(gameService, never()).initGame(eq(OWNER_KEY), any(GameInitRequest.class));
    }

    @Test
    @DisplayName("DB varchar 한계를 넘는 세계관 설정은 provider 호출 전에 거부한다")
    void initGame_RejectsWorldSettingLongerThanDatabaseLimit() throws Exception {
        mockMvc.perform(post("/api/game/init")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameInitRequest("가".repeat(256), "김대리"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(gameService, never()).initGame(eq(OWNER_KEY), any(GameInitRequest.class));
    }
}
