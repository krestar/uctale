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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiNarrativeAdapterTest {

    private static final String TEST_MODEL = "gemini-3.7-flash";

    private GeminiNarrativeAdapter adapter;
    private GeminiProviderSettings settings;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        settings = new GeminiProviderSettings("TEST_API_KEY", TEST_MODEL, "medium", "low");
        adapter = new GeminiNarrativeAdapter(new ObjectMapper(), builder, settings);
    }

    @Test
    @DisplayName("Gemini opening 요청은 설정 모델과 structured output, opening thinking level을 사용한다")
    void createOpening_UsesConfiguredModelStructuredOutputAndThinkingLevel() {
        mockServer.expect(requestTo(settings.generateContentUrl()))
                .andExpect(header("x-goog-api-key", "TEST_API_KEY"))
                .andExpect(content().string(containsString("\"responseMimeType\":\"application/json\"")))
                .andExpect(content().string(containsString("\"responseSchema\"")))
                .andExpect(content().string(containsString("\"thinkingLevel\":\"medium\"")))
                .andRespond(withSuccess(apiResponse(validNarrativeJson()), MediaType.APPLICATION_JSON));

        NarrativeTurn response = adapter.createOpening("좀비 아포칼립스", "김대리");

        assertThat(response.title()).isEqualTo("첫날 밤");
        assertThat(response.choices()).extracting(NarrativeTurn.Choice::text).containsExactly("도망간다");
        mockServer.verify();
    }

    @Test
    @DisplayName("progress 설정은 opening과 독립적인 thinking level을 request contract에 반영한다")
    void createRequestBody_UsesProgressThinkingLevel() throws Exception {
        String requestBody = adapter.createRequestBody("진행", settings.progressThinkingLevel());

        assertThat(requestBody)
                .contains("\"thinkingConfig\"")
                .contains("\"thinkingLevel\":\"low\"")
                .doesNotContain("thinkingBudget");
    }

    @Test
    @DisplayName("Gemini usageMetadata의 prompt candidate thought total token을 관측값으로 읽는다")
    void readTokenUsage_UsesProviderUsageMetadata() {
        Object usage = ReflectionTestUtils.invokeMethod(adapter, "readTokenUsage", apiResponse(validNarrativeJson()));
        Integer promptTokens = ReflectionTestUtils.invokeMethod(usage, "promptTokenCount");
        Integer candidateTokens = ReflectionTestUtils.invokeMethod(usage, "candidatesTokenCount");
        Integer thoughtTokens = ReflectionTestUtils.invokeMethod(usage, "thoughtsTokenCount");
        Integer totalTokens = ReflectionTestUtils.invokeMethod(usage, "totalTokenCount");

        assertThat(usage).isNotNull();
        assertThat(promptTokens).isEqualTo(100);
        assertThat(candidateTokens).isEqualTo(50);
        assertThat(thoughtTokens).isEqualTo(25);
        assertThat(totalTokens).isEqualTo(175);
    }

    @Test
    @DisplayName("누락 choice text를 기본값으로 보정하지 않고 recoverable 오류로 거부한다")
    void parseResponse_DoesNotDefaultMissingChoiceText() {
        String narrative = "{\"title\":\"첫날 밤\",\"story_text\":\"좀비가 나타났다!\",\"choices\":[{\"id\":1}]}";

        assertThatThrownBy(() -> adapter.parseResponse(apiResponse(narrative)))
                .isInstanceOfSatisfying(RecoverableNarrativeResponseException.class, exception ->
                        assertThat(exception.reasonCode()).isEqualTo("INVALID_CHOICE_TEXT"));
    }

    @Test
    @DisplayName("중복 choice id를 recoverable 오류로 거부한다")
    void parseResponse_RejectsDuplicateChoiceIds() {
        String narrative = "{\"title\":\"첫날 밤\",\"story_text\":\"좀비가 나타났다!\",\"choices\":[{\"id\":1,\"text\":\"도망간다\"},{\"id\":1,\"text\":\"숨는다\"}]}";

        assertThatThrownBy(() -> adapter.parseResponse(apiResponse(narrative)))
                .isInstanceOfSatisfying(RecoverableNarrativeResponseException.class, exception ->
                        assertThat(exception.reasonCode()).isEqualTo("INVALID_CHOICE_ID"));
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
    @DisplayName("repair 요청은 raw 응답 없이 실패 분류와 동일 opening thinking level만 전달한다")
    void repairOpening_UsesSafeReasonCodeAndOpeningThinkingLevel() {
        mockServer.expect(requestTo(settings.generateContentUrl()))
                .andExpect(header("x-goog-api-key", "TEST_API_KEY"))
                .andExpect(content().string(containsString("[응답 수정 요청]")))
                .andExpect(content().string(containsString("INVALID_CHOICE_ID")))
                .andExpect(content().string(containsString("\"thinkingLevel\":\"medium\"")))
                .andRespond(withSuccess(apiResponse(validNarrativeJson()), MediaType.APPLICATION_JSON));

        assertThat(adapter.repairOpening("세계", "캐릭터", "INVALID_CHOICE_ID").title()).isEqualTo("첫날 밤");
        mockServer.verify();
    }

    private String validNarrativeJson() {
        return "{\"title\":\"첫날 밤\",\"story_text\":\"좀비가 나타났다!\",\"choices\":[{\"id\":1,\"text\":\"도망간다\"}],\"visual_assets\":{\"background\":\"dark subway\",\"characters\":[],\"assets\":[]}}";
    }

    private String apiResponse(String narrativeJson) {
        try {
            String escaped = new ObjectMapper().writeValueAsString(narrativeJson);
            return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + escaped + "}]}}],"
                    + "\"usageMetadata\":{\"promptTokenCount\":100,\"candidatesTokenCount\":50,\"thoughtsTokenCount\":25,\"totalTokenCount\":175}}";
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
