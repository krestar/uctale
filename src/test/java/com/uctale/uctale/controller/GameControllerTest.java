package com.uctale.uctale.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.dto.GeminiResponse;
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

    @Mock
    private GameService gameService;

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
    @DisplayName("게임 초기화 응답은 명시적인 sessionId를 제공하고 잘못된 캐릭터 이미지 필드를 노출하지 않는다")
    void initGame_UsesExplicitSessionIdContract() throws Exception {
        GameInitRequest request = new GameInitRequest("좀비 아포칼립스", "김대리");
        GameResponse response = new GameResponse(
                42L,
                "첫날 밤",
                "오프닝 스토리입니다.",
                List.of(new GeminiResponse.Choice(1, "도망간다")),
                "/api/game/image?prompt=test&aspectRatio=16%3A9"
        );
        given(gameService.initGame(any(GameInitRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(42))
                .andExpect(jsonPath("$.title").value("첫날 밤"))
                .andExpect(jsonPath("$.storyText").value("오프닝 스토리입니다."))
                .andExpect(jsonPath("$.mainImageUrl").value("/api/game/image?prompt=test&aspectRatio=16%3A9"))
                .andExpect(jsonPath("$.characterImageUrl").doesNotExist())
                .andExpect(jsonPath("$.choices[0].id").value(1))
                .andExpect(jsonPath("$.choices[0].text").value("도망간다"));
    }

    @Test
    @DisplayName("게임 진행 요청 시 GameService의 다음 턴 응답을 반환한다")
    void progressGame_Success() throws Exception {
        GameProgressRequest request = new GameProgressRequest(42L, 1);
        GameResponse response = new GameResponse(
                42L,
                "두 번째 장면",
                "다음 턴 스토리입니다.",
                List.of(new GeminiResponse.Choice(2, "숨는다")),
                "/api/game/image?prompt=turn2&aspectRatio=16%3A9"
        );
        given(gameService.progressGame(any(GameProgressRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(42))
                .andExpect(jsonPath("$.storyText").value("다음 턴 스토리입니다."))
                .andExpect(jsonPath("$.choices[0].text").value("숨는다"));
    }

    @Test
    @DisplayName("빈 세계관 설정은 400으로 거부한다")
    void initGame_RejectsBlankWorldSetting() throws Exception {
        GameInitRequest request = new GameInitRequest("", "김대리");

        mockMvc.perform(post("/api/game/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("세계관 설정은 필수입니다."));
    }

    @Test
    @DisplayName("존재하지 않는 선택지 오류는 400으로 반환한다")
    void progressGame_MapsUnknownChoiceToBadRequest() throws Exception {
        given(gameService.progressGame(any(GameProgressRequest.class)))
                .willThrow(new IllegalArgumentException("존재하지 않는 선택지입니다."));

        mockMvc.perform(post("/api/game/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 999))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 선택지입니다."));
    }
}