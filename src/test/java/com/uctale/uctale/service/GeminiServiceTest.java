package com.uctale.uctale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GeminiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(GeminiService.class)
class GeminiServiceTest {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private MockRestServiceServer mockServer;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("오프닝 생성 요청 시, Gemini API가 반환한 JSON을 DTO로 잘 변환해야 한다")
    void getOpening_Success() {
        // given
        String world = "좀비 아포칼립스";
        String user = "김대리";
        GameInitRequest request = new GameInitRequest(world, user);

        String mockApiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{ \\"story_text\\": \\"좀비가 나타났다!\\", \\"choices\\": [], \\"visual_assets\\": { \\"background\\": \\"dark subway\\" } }"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=TEST_API_KEY"))
                .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON));

        GeminiResponse response = geminiService.getOpening(request);

        assertThat(response.story_text()).isEqualTo("좀비가 나타났다!");
        assertThat(response.visual_assets().background()).isEqualTo("dark subway");
    }
}