package com.uctale.uctale.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GeminiResponse;
import com.uctale.uctale.service.GeminiService;
import com.uctale.uctale.service.NanoBananaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private NanoBananaService nanoBananaService;

    @Test
    @DisplayName("게임 초기화 요청 시 스토리와 이미지를 조합하여 응답한다")
    void initGame_Success() throws Exception {
        // given
        GameInitRequest request = new GameInitRequest("좀비 아포칼립스", "김대리");

        GeminiResponse mockGeminiResponse = new GeminiResponse(
                "오프닝 스토리입니다.",
                List.of(new GeminiResponse.Choice(1, "도망간다")),
                new GeminiResponse.VisualAssets("dark street", List.of("zombie"), List.of())
        );
        given(geminiService.getOpening(any(GameInitRequest.class))).willReturn(mockGeminiResponse);

        given(nanoBananaService.generateImage(any(), any())).willReturn("data:image/jpeg;base64,TEST_IMAGE_DATA");

        // when & then
        mockMvc.perform(post("/api/game/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyText").value("오프닝 스토리입니다."))
                .andExpect(jsonPath("$.mainImageUrl").value("data:image/jpeg;base64,TEST_IMAGE_DATA"))
                .andExpect(jsonPath("$.choices[0].text").value("도망간다"));
    }
}