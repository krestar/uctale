package com.uctale.uctale.controller;

import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.service.GameService;
import jakarta.validation.Valid;
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

    @PostMapping("/verify-password")
    public ResponseEntity<?> verifyPassword(@RequestBody Map<String, String> payload) {
        String inputPassword = payload.get("password");
        if (accessPassword.equals(inputPassword)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(401).body("비밀번호가 틀렸습니다.");
    }

    @PostMapping("/init")
    public ResponseEntity<GameResponse> initGame(@Valid @RequestBody GameInitRequest request) {
        log.info("게임 초기화 요청 수신");
        return ResponseEntity.ok(gameService.initGame(request));
    }

    @PostMapping("/progress")
    public ResponseEntity<GameResponse> progressGame(@Valid @RequestBody GameProgressRequest request) {
        log.info("게임 진행 요청: 세션ID={}, 기대턴={}", request.sessionId(), request.expectedTurn());
        return ResponseEntity.ok(gameService.progressGame(request));
    }
}
