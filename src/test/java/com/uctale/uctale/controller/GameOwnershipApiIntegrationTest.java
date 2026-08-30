package com.uctale.uctale.controller;

import com.uctale.uctale.application.image.ImageGenerator;
import com.uctale.uctale.application.narrative.NarrativeContext;
import com.uctale.uctale.application.narrative.NarrativeGenerator;
import com.uctale.uctale.application.narrative.NarrativeTurn;
import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.security.AccessSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GameOwnershipApiIntegrationTest.ProviderTestConfig.class)
@Transactional
class GameOwnershipApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessSessionService accessSessionService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("다른 접근 주체는 session ID를 알아도 진행할 수 없고 소유자는 정상 진행한다")
    void progress_RejectsCrossOwnerAndAllowsOwner() throws Exception {
        AccessSessionService.IssuedSession ownerA = accessSessionService.authenticate("TEST_PASSWORD", null);
        AccessSessionService.IssuedSession ownerB = accessSessionService.authenticate("TEST_PASSWORD", null);

        MvcResult initResult = mockMvc.perform(post("/api/game/init")
                        .cookie(accessCookie(ownerA), ownerCookie(ownerA))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"worldSetting\":\"세계관\",\"characterSetting\":\"캐릭터\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnNumber").value(1))
                .andReturn();

        long sessionId = objectMapper.readTree(initResult.getResponse().getContentAsString())
                .get("sessionId")
                .asLong();
        String progressBody = "{\"sessionId\":" + sessionId + ",\"choiceId\":1,\"expectedTurn\":1}";

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
        ImageGenerator testImageGenerator() {
            return new ImageGenerator() {
                @Override
                public String createPublicUrl(String prompt, String aspectRatio) {
                    return "/api/game/image?prompt=test&aspectRatio=16%3A9";
                }

                @Override
                public GeneratedImage fetchImage(String prompt, String aspectRatio) {
                    return null;
                }
            };
        }
    }
}
