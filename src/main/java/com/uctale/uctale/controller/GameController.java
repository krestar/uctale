package com.uctale.uctale.controller;

import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameController {

    private final GameService gameService;

    // 게임 시작
    @PostMapping("/init")
    public ResponseEntity<GameResponse> initGame(@RequestBody GameInitRequest request) {
        log.info("게임 초기화: {}", request.worldSetting());
        return ResponseEntity.ok(gameService.initGame(request));
    }

    // 게임 진행 (선택지 클릭 시)
    @PostMapping("/progress")
    public ResponseEntity<GameResponse> progressGame(@RequestBody GameProgressRequest request) {
        log.info("게임 진행: 세션ID={}, 선택지={}", request.sessionId(), request.choiceId());
        return ResponseEntity.ok(gameService.progressGame(request));
    }
}