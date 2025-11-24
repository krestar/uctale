package com.uctale.uctale.controller;

import com.uctale.uctale.dto.GameInitRequest;
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

    @PostMapping("/init")
    public ResponseEntity<GameResponse> initGame(@RequestBody GameInitRequest request) {
        log.info("게임 초기화 요청: 세계관={}, 사용자={}", request.worldSetting(), request.characterSetting());

        // 서비스 계층으로 로직 위임
        GameResponse response = gameService.initGame(request);

        return ResponseEntity.ok(response);
    }
}