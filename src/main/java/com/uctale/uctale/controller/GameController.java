package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.service.GameService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @PostMapping("/init")
    public ResponseEntity<GameResponse> initGame(
            @RequestAttribute(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE) String ownerKey,
            @Valid @RequestBody GameInitRequest request,
            HttpServletRequest servletRequest
    ) {
        log.info("게임 초기화 요청 수신");
        CostRequestContext context = CostRequestContext.create(ownerKey, servletRequest.getRemoteAddr(), null, 1);
        return ResponseEntity.ok(gameService.initGame(context, request));
    }

    @PostMapping("/progress")
    public ResponseEntity<GameResponse> progressGame(
            @RequestAttribute(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE) String ownerKey,
            @Valid @RequestBody GameProgressRequest request,
            HttpServletRequest servletRequest
    ) {
        log.info("게임 진행 요청: 세션ID={}, 기대턴={}", request.sessionId(), request.expectedTurn());
        CostRequestContext context = CostRequestContext.create(
                ownerKey, servletRequest.getRemoteAddr(), request.sessionId(), request.expectedTurn() + 1
        );
        return ResponseEntity.ok(gameService.progressGame(context, request));
    }
}
