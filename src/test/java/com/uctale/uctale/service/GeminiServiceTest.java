package com.uctale.uctale.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GeminiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiServiceTest {

    private GeminiService geminiService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        geminiService = new GeminiService(new ObjectMapper(), builder);
        ReflectionTestUtils.setField(geminiService, "apiKey", "TEST_API_KEY");
    }

    @Test
    @DisplayName("오프닝 생성 요청 시 Gemini 응답 JSON을 DTO로 변환한다")
    void getOpening_Success() {
        GameInitRequest request = new GameInitRequest("좀비 아포칼립스", "김대리");

        String mockApiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{ \\"title\\": \\"첫날 밤\\", \\"story_text\\": \\"좀비가 나타났다!\\", \\"choices\\": [], \\"visual_assets\\": { \\"background\\": \\"dark subway\\", \\"characters\\": [], \\"assets\\": [] } }"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=TEST_API_KEY"))
                .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON));

        GeminiResponse response = geminiService.getOpening(request);

        assertThat(response.title()).isEqualTo("첫날 밤");
        assertThat(response.story_text()).isEqualTo("좀비가 나타났다!");
        assertThat(response.visual_assets().background()).isEqualTo("dark subway");
        mockServer.verify();
    }
}
