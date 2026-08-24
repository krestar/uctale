package com.uctale.uctale.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.dto.GeminiResponse;
import com.uctale.uctale.service.GameService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GameService gameService;

    @Test
    @DisplayName("게임 초기화 요청 시 GameService 응답을 그대로 반환한다")
    void initGame_Success() throws Exception {
        GameInitRequest request = new GameInitRequest("좀비 아포칼립스", "김대리");
        GameResponse response = new GameResponse(
                "첫날 밤",
                "오프닝 스토리입니다.",
                List.of(new GeminiResponse.Choice(1, "도망간다")),
                "https://example.com/opening.png",
                "42"
        );
        given(gameService.initGame(any(GameInitRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("첫날 밤"))
                .andExpect(jsonPath("$.storyText").value("오프닝 스토리입니다."))
                .andExpect(jsonPath("$.mainImageUrl").value("https://example.com/opening.png"))
                .andExpect(jsonPath("$.characterImageUrl").value("42"))
                .andExpect(jsonPath("$.choices[0].id").value(1))
                .andExpect(jsonPath("$.choices[0].text").value("도망간다"));
    }

    @Test
    @DisplayName("게임 진행 요청 시 GameService의 다음 턴 응답을 반환한다")
    void progressGame_Success() throws Exception {
        GameProgressRequest request = new GameProgressRequest(42L, 1);
        GameResponse response = new GameResponse(
                "두 번째 장면",
                "다음 턴 스토리입니다.",
                List.of(new GeminiResponse.Choice(2, "숨는다")),
                "https://example.com/turn-2.png",
                "42"
        );
        given(gameService.progressGame(any(GameProgressRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyText").value("다음 턴 스토리입니다."))
                .andExpect(jsonPath("$.choices[0].text").value("숨는다"));
    }
}
