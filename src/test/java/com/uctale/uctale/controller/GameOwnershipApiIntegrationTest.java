package com.uctale.uctale.controller;

import com.uctale.uctale.application.image.ImageGenerator;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.security.AccessSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(GameOwnershipApiIntegrationTest.ProviderTestConfig.class)
@Transactional
class GameOwnershipApiIntegrationTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private AccessSessionService accessSessionService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CountingImageGenerator countingImageGenerator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        countingImageGenerator.reset();
    }

    @Test
    @DisplayName("다른 접근 주체는 session과 image asset을 사용할 수 없고 소유자는 asset 결과를 재사용한다")
    void ownership_IsEnforcedForProgressAndImageAsset() throws Exception {
        AccessSessionService.IssuedSession ownerA = accessSessionService.authenticate("TEST_PASSWORD", null);
        AccessSessionService.IssuedSession ownerB = accessSessionService.authenticate("TEST_PASSWORD", null);

        MvcResult initResult = mockMvc.perform(post("/api/game/init")
                        .cookie(accessCookie(ownerA), ownerCookie(ownerA))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"worldSetting\":\"세계관\",\"characterSetting\":\"캐릭터\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnNumber").value(1))
                .andExpect(jsonPath("$.mainImageUrl").value(org.hamcrest.Matchers.startsWith("/api/game/image-assets/")))
                .andReturn();

        var responseJson = objectMapper.readTree(initResult.getResponse().getContentAsString());
        long sessionId = responseJson.get("sessionId").asLong();
        String imageUrl = responseJson.get("mainImageUrl").asText();
        String progressBody = "{\"sessionId\":" + sessionId + ",\"choiceId\":1,\"expectedTurn\":1}";

        mockMvc.perform(get(imageUrl)
                        .cookie(accessCookie(ownerB), ownerCookie(ownerB))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_ASSET_NOT_FOUND"));

        mockMvc.perform(get(imageUrl)
                        .cookie(accessCookie(ownerA), ownerCookie(ownerA))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes("fake-image".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(imageUrl)
                        .cookie(accessCookie(ownerA), ownerCookie(ownerA))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isOk());

        assertThat(countingImageGenerator.calls()).isEqualTo(1);

        mockMvc.perform(post("/api/game/progress")
                        .cookie(accessCookie(ownerB), ownerCookie(ownerB))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(progressBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        mockMvc.perform(post("/api/game/progress")
                        .cookie(accessCookie(ownerA), ownerCookie(ownerA))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(progressBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.turnNumber").value(2));
    }

    private Cookie accessCookie(AccessSessionService.IssuedSession session) {
        return new Cookie(AccessSessionService.COOKIE_NAME, session.accessToken());
    }

    private Cookie ownerCookie(AccessSessionService.IssuedSession session) {
        return new Cookie(AccessSessionService.OWNER_COOKIE_NAME, session.ownerToken());
    }

    @TestConfiguration
    static class ProviderTestConfig {

        @Bean
        @Primary
        NarrativeGenerator testNarrativeGenerator() {
            return new NarrativeGenerator() {
                @Override
                public NarrativeTurn createOpening(String worldSetting, String characterSetting) {
                    return turn("첫 장면", "첫 이야기", "거리");
                }

                @Override
                public NarrativeTurn createNextTurn(NarrativeContext context) {
                    return turn("다음 장면", "다음 이야기", "골목");
                }

                private NarrativeTurn turn(String title, String story, String scene) {
                    return new NarrativeTurn(
                            title,
                            story,
                            List.of(new NarrativeTurn.Choice(1, "진행한다")),
                            new NarrativeTurn.VisualAssets(scene, List.of(), List.of())
                    );
                }
            };
        }

        @Bean
        @Primary
        CountingImageGenerator testImageGenerator() {
            return new CountingImageGenerator();
        }
    }

    static class CountingImageGenerator implements ImageGenerator {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public GeneratedImage fetchImage(String prompt, String aspectRatio) {
            calls.incrementAndGet();
            return new GeneratedImage("fake-image".getBytes(StandardCharsets.UTF_8), MediaType.IMAGE_JPEG);
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }
}
