package com.uctale.uctale.controller;

import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.dto.GeminiResponse;
import com.uctale.uctale.service.GeminiService;
import com.uctale.uctale.service.NanoBananaService;
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

    private final GeminiService geminiService;
    private final NanoBananaService nanoBananaService;

    @PostMapping("/init")
    public ResponseEntity<GameResponse> initGame(@RequestBody GameInitRequest request) {
        log.info("게임 초기화 요청: 세계관={}, 사용자={}", request.worldSetting(), request.characterSetting());

        // 1. 텍스트(스토리) 생성
        GeminiResponse geminiResponse = geminiService.getOpening(request);

        // 2. 이미지 생성 (배경)
        String bgPrompt = geminiResponse.visual_assets().background();
        String mainImageUrl = nanoBananaService.generateImage(bgPrompt, "16:9");

        // 3. 응답 조합
        GameResponse response = new GameResponse(
                geminiResponse.story_text(),
                geminiResponse.choices(),
                mainImageUrl,
                null // 캐릭터 이미지는 추후 확장
        );

        return ResponseEntity.ok(response);
    }
}