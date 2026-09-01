package com.uctale.uctale.provider.gemini;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.application.narrative.RecoverableNarrativeResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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
    @DisplayName("Gemini 요청은 JSON MIME과 response schema를 함께 전달한다")
    void createOpening_UsesStructuredOutputSchema() {
        mockServer.expect(requestTo(geminiUrl()))
                .andExpect(content().string(containsString("\"response_mime_type\":\"application/json\"")))
                .andExpect(content().string(containsString("\"response_schema\"")))
                .andRespond(withSuccess(apiResponse(validNarrativeJson()), MediaType.APPLICATION_JSON));
        NarrativeTurn response = adapter.createOpening("좀비 아포칼립스", "김대리");
        assertThat(response.title()).isEqualTo("첫날 밤");
        assertThat(response.choices()).extracting(NarrativeTurn.Choice::text).containsExactly("도망간다");
        mockServer.verify();
    }

    @Test
    @DisplayName("누락 choice text를 기본값으로 보정하지 않고 recoverable 오류로 거부한다")
    void parseResponse_DoesNotDefaultMissingChoiceText() {
        String narrative = """{"title":"첫날 밤","story_text":"좀비가 나타났다!","choices":[{"id":1}]}""";
        assertThatThrownBy(() -> adapter.parseResponse(apiResponse(narrative)))
                .isInstanceOfSatisfying(RecoverableNarrativeResponseException.class, exception -> assertThat(exception.reasonCode()).isEqualTo("INVALID_CHOICE_TEXT"));
    }

    @Test
    @DisplayName("중복 choice id를 recoverable 오류로 거부한다")
    void parseResponse_RejectsDuplicateChoiceIds() {
        String narrative = """{"title":"첫날 밤","story_text":"좀비가 나타났다!","choices":[{"id":1,"text":"도망간다"},{"id":1,"text":"숨는다"}]}""";
        assertThatThrownBy(() -> adapter.parseResponse(apiResponse(narrative)))
                .isInstanceOfSatisfying(RecoverableNarrativeResponseException.class, exception -> assertThat(exception.reasonCode()).isEqualTo("INVALID_CHOICE_ID"));
    }

    @Test
    @DisplayName("깨진 nested JSON은 raw 응답을 노출하지 않는 분류 코드로 거부한다")
    void parseResponse_ClassifiesMalformedJson() {
        assertThatThrownBy(() -> adapter.parseResponse(apiResponse("{not-json}")))
                .isInstanceOfSatisfying(RecoverableNarrativeResponseException.class, exception -> {
                    assertThat(exception.reasonCode()).isEqualTo("MALFORMED_JSON");
                    assertThat(exception.getMessage()).doesNotContain("{not-json}");
                });
    }

    @Test
    @DisplayName("repair 요청은 raw 응답 없이 실패 분류만 prompt에 전달한다")
    void repairOpening_UsesSafeReasonCode() {
        mockServer.expect(requestTo(geminiUrl()))
                .andExpect(content().string(containsString("[응답 수정 요청]")))
                .andExpect(content().string(containsString("INVALID_CHOICE_ID")))
                .andRespond(withSuccess(apiResponse(validNarrativeJson()), MediaType.APPLICATION_JSON));
        assertThat(adapter.repairOpening("세계", "캐릭터", "INVALID_CHOICE_ID").title()).isEqualTo("첫날 밤");
        mockServer.verify();
    }

    private String geminiUrl() { return "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=TEST_API_KEY"; }
    private String validNarrativeJson() { return """{"title":"첫날 밤","story_text":"좀비가 나타났다!","choices":[{"id":1,"text":"도망간다"}],"visual_assets":{"background":"dark subway","characters":[],"assets":[]}}"""; }
    private String apiResponse(String narrativeJson) {
        try {
            String escaped = new ObjectMapper().writeValueAsString(narrativeJson);
            return """{"candidates":[{"content":{"parts":[{"text":%s}]}}]}""".formatted(escaped);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
