package com.uctale.uctale.controller;

import tools.jackson.databind.ObjectMapper;
import com.uctale.uctale.application.cost.ClientIpResolver;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.game.GameSessionNotFoundException;
import com.uctale.uctale.application.game.IdempotencyConflictException;
import com.uctale.uctale.application.game.InvalidChoiceException;
import com.uctale.uctale.application.game.TurnConflictException;
import com.uctale.uctale.dto.GameChoice;
import com.uctale.uctale.dto.GameInitRequest;
import com.uctale.uctale.dto.GameProgressRequest;
import com.uctale.uctale.dto.GameResponse;
import com.uctale.uctale.security.AccessSessionInterceptor;
import com.uctale.uctale.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String IDEMPOTENCY_KEY = "12345678-test-key";

    @Mock private GameService gameService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GameController(gameService, new ClientIpResolver(false)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("게임 초기화 응답은 첫 번째 턴과 idempotency key를 비용 context에 전달한다")
    void initGame_ReturnsFirstTurn() throws Exception {
        GameResponse response = new GameResponse(42L, 1, "첫날 밤", "오프닝 스토리입니다.",
                List.of(new GameChoice(1, "도망간다")), "/api/game/image-assets/asset-1");
        given(gameService.initGame(any(CostRequestContext.class), any(GameInitRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/init")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameInitRequest("좀비 아포칼립스", "김대리"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(42))
                .andExpect(jsonPath("$.turnNumber").value(1));

        ArgumentCaptor<CostRequestContext> contextCaptor = ArgumentCaptor.forClass(CostRequestContext.class);
        verify(gameService).initGame(contextCaptor.capture(), any(GameInitRequest.class));
        assertThat(contextCaptor.getValue().ownerKey()).isEqualTo(OWNER_KEY);
        assertThat(contextCaptor.getValue().turn()).isEqualTo(1);
        assertThat(contextCaptor.getValue().idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName("게임 진행 요청은 session, 다음 턴, idempotency key를 비용 context에 포함한다")
    void progressGame_Success() throws Exception {
        GameProgressRequest request = new GameProgressRequest(42L, 1, 1);
        GameResponse response = new GameResponse(42L, 2, "두 번째 장면", "다음 턴 스토리입니다.",
                List.of(new GameChoice(2, "숨는다")), "/api/game/image-assets/asset-2");
        given(gameService.progressGame(any(CostRequestContext.class), any(GameProgressRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnNumber").value(2));

        ArgumentCaptor<CostRequestContext> contextCaptor = ArgumentCaptor.forClass(CostRequestContext.class);
        verify(gameService).progressGame(contextCaptor.capture(), any(GameProgressRequest.class));
        assertThat(contextCaptor.getValue().sessionId()).isEqualTo(42L);
        assertThat(contextCaptor.getValue().turn()).isEqualTo(2);
        assertThat(contextCaptor.getValue().idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void progressGame_MapsOwnershipMismatchToNotFound() throws Exception {
        given(gameService.progressGame(any(CostRequestContext.class), any(GameProgressRequest.class)))
                .willThrow(new GameSessionNotFoundException("존재하지 않는 세션입니다."));
        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 1, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void progressGame_MapsTurnConflict() throws Exception {
        given(gameService.progressGame(any(CostRequestContext.class), any(GameProgressRequest.class)))
                .willThrow(new TurnConflictException("이미 처리되었거나 오래된 턴 요청입니다."));
        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 1, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TURN_CONFLICT"));
    }

    @Test
    void progressGame_MapsIdempotencyConflict() throws Exception {
        given(gameService.progressGame(any(CostRequestContext.class), any(GameProgressRequest.class)))
                .willThrow(new IdempotencyConflictException("key conflict"));
        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 1, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void progressGame_MapsInvalidChoice() throws Exception {
        given(gameService.progressGame(any(CostRequestContext.class), any(GameProgressRequest.class)))
                .willThrow(new InvalidChoiceException("현재 턴에서 선택할 수 없는 선택지입니다."));
        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameProgressRequest(42L, 99, 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CHOICE"));
    }

    @Test
    void initGame_RejectsBlankWorldSetting() throws Exception {
        mockMvc.perform(post("/api/game/init")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameInitRequest("", "김대리"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(gameService, never()).initGame(any(CostRequestContext.class), any(GameInitRequest.class));
    }

    @Test
    @DisplayName("progress arguments 엔트리 수 상한 초과는 service 진입 전에 거부한다")
    void progressGame_RejectsTooManyArgumentsBeforeService() throws Exception {
        Map<String, String> arguments = new LinkedHashMap<>();
        for (int i = 0; i < 9; i++) {
            arguments.put("k" + i, "v" + i);
        }
        GameProgressRequest request = new GameProgressRequest(
                42L, 1, 1, "token", "NARRATIVE_CHOICE", 1, arguments
        );

        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(gameService, never()).progressGame(any(CostRequestContext.class), any(GameProgressRequest.class));
    }

    @Test
    @DisplayName("progress argument key/value 길이 상한 초과는 service 진입 전에 거부한다")
    void progressGame_RejectsOversizedArgumentKeyAndValueBeforeService() throws Exception {
        GameProgressRequest oversizedKey = new GameProgressRequest(
                42L, 1, 1, "token", "NARRATIVE_CHOICE", 1, Map.of("k".repeat(65), "v")
        );
        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        GameProgressRequest oversizedValue = new GameProgressRequest(
                42L, 1, 1, "token", "NARRATIVE_CHOICE", 1, Map.of("key", "v".repeat(257))
        );
        mockMvc.perform(post("/api/game/progress")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedValue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(gameService, never()).progressGame(any(CostRequestContext.class), any(GameProgressRequest.class));
    }

    @Test
    void initGame_RejectsWorldSettingLongerThanDatabaseLimit() throws Exception {
        mockMvc.perform(post("/api/game/init")
                        .requestAttr(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE, OWNER_KEY)
                        .header(GameController.IDEMPOTENCY_KEY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GameInitRequest("가".repeat(256), "김대리"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(gameService, never()).initGame(any(CostRequestContext.class), any(GameInitRequest.class));
    }
}
