package com.uctale.uctale.controller;

import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GameController {

    private final GameService gameService;

    @Value("${game.access.password}")
    private String accessPassword;

    // 비밀번호 검증 API
    @PostMapping("/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestBody Map<String, String> payload) {
        String inputPassword = payload.get("password");
        if (accessPassword.equals(inputPassword)) {
            return ResponseEntity.ok().build(); // 성공 (200 OK)
        } else {
            return ResponseEntity.status(401).body("비밀번호가 틀렸습니다."); // 실패 (401 Unauthorized)
        }
    }

    @PostMapping("/init")
    public ResponseEntity<GameResponse> initGame(@RequestBody GameInitRequest request) {
        log.info("게임 초기화 요청: 세계관={}, 사용자={}", request.worldSetting(), request.characterSetting());
        return ResponseEntity.ok(gameService.initGame(request));
    }

    @PostMapping("/progress")
    public ResponseEntity<GameResponse> progressGame(@RequestBody GameProgressRequest request) {
        log.info("게임 진행: 세션ID={}, 선택지={}", request.sessionId(), request.choiceId());
        return ResponseEntity.ok(gameService.progressGame(request));
    }
}