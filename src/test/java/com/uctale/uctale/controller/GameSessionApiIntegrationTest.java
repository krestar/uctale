package com.uctale.uctale.controller;

import com.uctale.uctale.application.game.ChoiceCodec;
import com.uctale.uctale.application.game.GameMutationRequestService;
import com.uctale.uctale.application.game.GamePersistenceService;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.security.AccessSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class GameSessionApiIntegrationTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private AccessSessionService accessSessionService;
    @Autowired private GamePersistenceService persistenceService;
    @Autowired private GameMutationRequestService mutationRequestService;
    @Autowired private ChoiceCodec choiceCodec;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("세션 목록과 재개는 owner별로 격리되고 같은 완료 turn의 story/state/actions를 반환한다")
    void listAndResume_AreOwnerScopedAndTurnConsistent() throws Exception {
        AccessSessionService.IssuedSession ownerA = accessSessionService.authenticate("TEST_PASSWORD", null);
        AccessSessionService.IssuedSession ownerB = accessSessionService.authenticate("TEST_PASSWORD", null);
        var sessionA = persistenceService.saveOpening(
                ownerA.ownerKey(), "A 세계", "A 인물", "A의 첫 이야기",
                choiceCodec.serialize(List.of(new GameChoice(1, "A 선택"))), null
        );
        persistenceService.saveOpening(
                ownerB.ownerKey(), "B 세계", "B 인물", "B의 첫 이야기",
                choiceCodec.serialize(List.of(new GameChoice(1, "B 선택"))), null
        );

        mockMvc.perform(get("/api/game/sessions")
                        .cookie(accessCookie(ownerA), ownerCookie(ownerA))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value(sessionA.getId()))
                .andExpect(jsonPath("$[0].currentTurn").value(1))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].canResume").value(true));

        mockMvc.perform(get("/api/game/sessions/{sessionId}", sessionA.getId())
                        .cookie(accessCookie(ownerA), ownerCookie(ownerA))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnNumber").value(1))
                .andExpect(jsonPath("$.canonicalStateTurn").value(1))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.canProgress").value(true))
                .andExpect(jsonPath("$.game.turnNumber").value(1))
                .andExpect(jsonPath("$.game.storyText").value("A의 첫 이야기"))
                .andExpect(jsonPath("$.game.choices[0].text").value("A 선택"));

        mockMvc.perform(get("/api/game/sessions/{sessionId}", sessionA.getId())
                        .cookie(accessCookie(ownerB), ownerCookie(ownerB))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("유효 lease가 있으면 마지막 완료 turn을 표시하되 새 선택은 잠근다")
    void resume_ActiveProcessingLeaseShowsLastCompletedTurnWithoutProgress() throws Exception {
        AccessSessionService.IssuedSession owner = accessSessionService.authenticate("TEST_PASSWORD", null);
        var session = persistenceService.saveOpening(
                owner.ownerKey(), "처리 세계", "처리 인물", "완료된 첫 이야기",
                choiceCodec.serialize(List.of(new GameChoice(1, "계속한다"))), null
        );
        mutationRequestService.begin(
                owner.ownerKey(), GameMutationRequestService.PROGRESS, "resume-processing-001",
                session.getId(), 1, "a".repeat(64)
        );

        mockMvc.perform(get("/api/game/sessions/{sessionId}", session.getId())
                        .cookie(accessCookie(owner), ownerCookie(owner))
                        .header(AccessSessionInterceptor.CLIENT_HEADER, AccessSessionInterceptor.CLIENT_HEADER_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.canProgress").value(false))
                .andExpect(jsonPath("$.game.turnNumber").value(1))
                .andExpect(jsonPath("$.game.storyText").value("완료된 첫 이야기"));
    }

    private Cookie accessCookie(AccessSessionService.IssuedSession session) {
        return new Cookie(AccessSessionService.COOKIE_NAME, session.accessToken());
    }

    private Cookie ownerCookie(AccessSessionService.IssuedSession session) {
        return new Cookie(AccessSessionService.OWNER_COOKIE_NAME, session.ownerToken());
    }
}
