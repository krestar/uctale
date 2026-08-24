package com.uctale.uctale.provider.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.narrative.NarrativeTurn;
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

class GeminiNarrativeAdapterTest {

    private GeminiNarrativeAdapter adapter;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new GeminiNarrativeAdapter(new ObjectMapper(), builder);
        ReflectionTestUtils.setField(adapter, "apiKey", "TEST_API_KEY");
    }

    @Test
    @DisplayName("Gemini 오프닝 응답을 내부 내러티브 모델로 변환한다")
    void createOpening_MapsProviderResponse() {
        String mockApiResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{ \\"title\\": \\"첫날 밤\\", \\"story_text\\": \\"좀비가 나타났다!\\", \\"choices\\": [{\\"id\\":1,\\"text\\":\\"도망간다\\"}], \\"visual_assets\\": { \\"background\\": \\"dark subway\\", \\"characters\\": [], \\"assets\\": [] } }"
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=TEST_API_KEY"))
                .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON));

        NarrativeTurn response = adapter.createOpening("좀비 아포칼립스", "김대리");

        assertThat(response.title()).isEqualTo("첫날 밤");
        assertThat(response.storyText()).isEqualTo("좀비가 나타났다!");
        assertThat(response.choices()).extracting(NarrativeTurn.Choice::text).containsExactly("도망간다");
        assertThat(response.visualAssets().background()).isEqualTo("dark subway");
        mockServer.verify();
    }
}
