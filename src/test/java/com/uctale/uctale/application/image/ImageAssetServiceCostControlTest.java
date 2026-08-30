package com.uctale.uctale.application.image;

import com.uctale.uctale.application.cost.CostOperation;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.application.cost.RateLimitExceededException;
import com.uctale.uctale.domain.GameSession;
import com.uctale.uctale.domain.ImageAsset;
import com.uctale.uctale.repository.ImageAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ImageAssetServiceCostControlTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private ImageAssetRepository repository;
    @Mock private ImageGenerator imageGenerator;
    @Mock private CostRateLimiter rateLimiter;
    @Mock private ProviderCallTelemetry telemetry;
    @Mock private ImageGenerationPolicy generationPolicy;

    private ImageAssetService service;

    @BeforeEach
    void setUp() {
        service = new ImageAssetService(repository, imageGenerator, rateLimiter, telemetry, generationPolicy);
    }

    @Test
    @DisplayName("이미 생성된 asset 재조회는 Image quota를 소비하지 않는다")
    void generatedAsset_DoesNotConsumeQuota() {
        ImageAsset asset = asset("asset-1");
        asset.storeGeneratedImage(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG.toString());
        given(repository.findByIdAndGameSessionOwnerKey("asset-1", OWNER_KEY)).willReturn(Optional.of(asset));

        ImageAssetService.GeneratedAsset result = service.getOrGenerate(
                new CostRequestContext("r1", OWNER_KEY, "1.2.3.4", null, null, null), "asset-1"
        );

        assertThat(result.bytes()).containsExactly(1, 2, 3);
        verify(rateLimiter, never()).check(any(), any());
        verify(imageGenerator, never()).fetchImage(any(ImageGenerator.GenerationRequest.class));
        verifyNoInteractions(telemetry);
    }

    @Test
    @DisplayName("미생성 asset의 rate limit 초과는 provider 호출 전에 거부한다")
    void rateLimit_RejectsBeforeProvider() {
        ImageAsset asset = asset("asset-2");
        given(repository.findByIdAndGameSessionOwnerKey("asset-2", OWNER_KEY)).willReturn(Optional.of(asset));
        doThrow(new RateLimitExceededException("limit", 10))
                .when(rateLimiter).check(eq(CostOperation.IMAGE), any(CostRequestContext.class));

        assertThatThrownBy(() -> service.getOrGenerate(
                new CostRequestContext("r2", OWNER_KEY, "1.2.3.4", null, null, null), "asset-2"
        )).isInstanceOf(RateLimitExceededException.class);

        verify(imageGenerator, never()).fetchImage(any(ImageGenerator.GenerationRequest.class));
        verifyNoInteractions(telemetry);
    }

    @Test
    @DisplayName("provider 최종 실패는 canonical turn과 분리된 정적 placeholder로 degrade한다")
    void providerFailure_ReturnsPlaceholder() {
        ImageAsset asset = asset("asset-3");
        given(repository.findByIdAndGameSessionOwnerKey("asset-3", OWNER_KEY)).willReturn(Optional.of(asset));
        given(telemetry.observe(eq("pollinations"), eq("image_generation"), any(), eq(0), any()))
                .willThrow(new ImageGenerationException("provider failed"));

        ImageAssetService.GeneratedAsset result = service.getOrGenerate(
                new CostRequestContext("r3", OWNER_KEY, "1.2.3.4", null, null, null), "asset-3"
        );

        assertThat(result.contentType().toString()).isEqualTo("image/svg+xml");
        assertThat(new String(result.bytes())).contains("UCTale scene unavailable");
        verify(repository, never()).saveAndFlush(any());
    }

    private ImageAsset asset(String id) {
        GameSession session = new GameSession(OWNER_KEY, "world", "character");
        ReflectionTestUtils.setField(session, "id", 42L);
        return new ImageAsset(
                id, session, 1, "prompt", "16:9", "flux", 1024, 576, 123, true, "uctale-charcoal-v1"
        );
    }
}
