package com.uctale.uctale.controller;

import com.uctale.uctale.application.game.GameSessionQueryService;
import com.uctale.uctale.dto.SessionResumeResponse;
import com.uctale.uctale.dto.SessionSummaryResponse;
import com.uctale.uctale.security.AccessSessionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/game/sessions")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionQueryService queryService;

    @GetMapping
    public ResponseEntity<List<SessionSummaryResponse>> listSessions(
            @RequestAttribute(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE) String ownerKey
    ) {
        return ResponseEntity.ok(queryService.listSessions(ownerKey));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResumeResponse> resumeSession(
            @RequestAttribute(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE) String ownerKey,
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(queryService.resumeSession(ownerKey, sessionId));
    }
}
