package com.uctale.uctale.provider.gemini;

import com.uctale.uctale.application.narrative.StoryMemorySummaryDraft;
import com.uctale.uctale.domain.game.GameTurn;
import com.uctale.uctale.domain.game.StorySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiStoryMemorySummarizerTest {

    private GeminiStoryMemorySummarizer summarizer;
    private GeminiProviderSettings settings;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        settings = new GeminiProviderSettings("TEST_API_KEY", "gemini-3.7-flash", "medium", "low");
        summarizer = new GeminiStoryMemorySummarizer(new ObjectMapper(), builder, settings);
    }

    @Test
    @DisplayName("summary prompt는 canonical state 비소유 계약과 source range/state version을 명시한다")
    void summarize_UsesBoundedNarrativeMemoryContract() throws Exception {
        mockServer.expect(requestTo(settings.generateContentUrl()))
                .andExpect(header("x-goog-api-key", "TEST_API_KEY"))
                .andExpect(content().string(containsString("HP/MP")))
                .andExpect(content().string(containsString("inventory/equipment")))
                .andExpect(content().string(containsString("quest/objective/flag")))
                .andExpect(content().string(containsString("NPC affinity/stage")))
                .andExpect(content().string(containsString("source_from_turn=1, source_to_turn=2, state_version=3")))
                .andExpect(content().string(containsString("referenced_canonical_keys")))
                .andRespond(withSuccess(apiResponse(), MediaType.APPLICATION_JSON));

        StoryMemorySummaryDraft draft = summarizer.summarize(
                StorySummary.empty(),
                List.of(
                        new GameTurn(1, "", "오프닝"),
                        new GameTurn(2, "북문으로 간다", "안내인을 만났다")
                ),
                3
        );

        assertThat(draft.sourceFromTurn()).isEqualTo(1);
        assertThat(draft.sourceToTurn()).isEqualTo(2);
        assertThat(draft.stateVersion()).isEqualTo(3);
        assertThat(draft.referencedCanonicalKeys()).isEmpty();
        mockServer.verify();
    }

    private String apiResponse() throws Exception {
        String draft = "{\"source_from_turn\":1,\"source_to_turn\":2,\"state_version\":3,\"text\":\"안내인을 만났다.\",\"referenced_canonical_keys\":[]}";
        String escaped = new ObjectMapper().writeValueAsString(draft);
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + escaped + "}]}}]}";
    }
}
