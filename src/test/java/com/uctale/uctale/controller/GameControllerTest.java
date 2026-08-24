package com.uctale.uctale.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {

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
    @DisplayName("게임 초기화 응답은 첫 번째 턴을 제공한다")
    void initGame_ReturnsFirstTurn() throws Exception {
        GameResponse response = new GameResponse(
                42L,
                1,
                "첫날 밤",
                "오프닝 스토리입니다.",
                List.of(new GameChoice(1, "도망간다")),
                "/api/game/image?prompt=test&aspectRatio=16%3A9"
        );
        given(gameService.initGame(any(GameInitRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameInitRequest("좀비 아포칼립스", "김대리"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(42))
                .andExpect(jsonPath("$.turnNumber").value(1));
    }

    @Test
    @DisplayName("게임 진행 요청은 기대 턴과 함께 다음 턴을 반환한다")
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
        given(gameService.progressGame(any(GameProgressRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnNumber").value(2));
    }

    @Test
    @DisplayName("오래된 턴 요청은 409로 반환한다")
    void progressGame_MapsTurnConflict() throws Exception {
        given(gameService.progressGame(any(GameProgressRequest.class)))
                .willThrow(new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다."));

        mockMvc.perform(post("/api/game/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 1, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 처리되었거나 오래된 턴 요청입니다."));
    }

    @Test
    @DisplayName("빈 세계관 설정은 400으로 거부한다")
    void initGame_RejectsBlankWorldSetting() throws Exception {
        mockMvc.perform(post("/api/game/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameInitRequest("", "김대리"))))
                .andExpect(status().isBadRequest());
    }
}
