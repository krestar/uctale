package com.uctale.uctale.application.image;

import com.uctale.uctale.application.cost.CostOperation;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.domain.ImageAsset;
import com.uctale.uctale.repository.ImageAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ImageAssetService {

    private static final byte[] FALLBACK_IMAGE = ("""
            <svg xmlns="http://www.w3.org/2000/svg" width="1024" height="576" viewBox="0 0 1024 576">
              <rect width="1024" height="576" fill="#f2f0eb"/>
              <path d="M160 420 L360 220 L500 350 L650 180 L864 420" fill="none" stroke="#444" stroke-width="14" opacity="0.7"/>
              <text x="512" y="500" text-anchor="middle" font-family="sans-serif" font-size="30" fill="#555">UCTale scene unavailable</text>
            </svg>
            """).getBytes(StandardCharsets.UTF_8);

    private final ImageAssetRepository imageAssetRepository;
    private final ImageGenerator imageGenerator;
    private final CostRateLimiter costRateLimiter;
    private final ProviderCallTelemetry providerCallTelemetry;
    private final ImageGenerationPolicy generationPolicy;
    private final ConcurrentHashMap<String, Object> generationLocks = new ConcurrentHashMap<>();

    public ImageAssetService(
            ImageAssetRepository imageAssetRepository,
            ImageGenerator imageGenerator,
            CostRateLimiter costRateLimiter,
            ProviderCallTelemetry providerCallTelemetry,
            ImageGenerationPolicy generationPolicy
    ) {
        this.imageAssetRepository = imageAssetRepository;
        this.imageGenerator = imageGenerator;
        this.costRateLimiter = costRateLimiter;
        this.providerCallTelemetry = providerCallTelemetry;
        this.generationPolicy = generationPolicy;
    }

    public AssetReference issue(String prompt, String aspectRatio) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("이미지 prompt는 필수입니다.");
        }
        String normalizedAspectRatio = "1:1".equals(aspectRatio) ? "1:1" : "16:9";
        ImageGenerationPolicy.GenerationSpec spec = generationPolicy.issue(normalizedAspectRatio);
        String id = UUID.randomUUID().toString();
        return new AssetReference(
                id,
                "/api/game/image-assets/" + id,
                prompt,
                normalizedAspectRatio,
                spec.model(),
                spec.width(),
                spec.height(),
                spec.seed(),
                spec.safe(),
                spec.styleVersion()
        );
    }

    public GeneratedAsset getOrGenerate(String ownerKey, String assetId) {
        return getOrGenerate(CostRequestContext.internal(ownerKey, null, null), assetId);
    }

    public GeneratedAsset getOrGenerate(CostRequestContext requestContext, String assetId) {
        ImageAsset asset = findOwnedAsset(requestContext.ownerKey(), assetId);
        if (asset.generated()) {
            return toGeneratedAsset(asset);
        }

        Object lock = generationLocks.computeIfAbsent(assetId, ignored -> new Object());
        try {
            synchronized (lock) {
                ImageAsset current = findOwnedAsset(requestContext.ownerKey(), assetId);
                if (current.generated()) {
                    return toGeneratedAsset(current);
                }

                CostRequestContext providerContext = new CostRequestContext(
                        requestContext.requestId(),
                        requestContext.ownerKey(),
                        requestContext.clientIp(),
                        current.getGameSession().getId(),
                        current.getTurnNumber(),
                        requestContext.idempotencyKey()
                );
                costRateLimiter.check(CostOperation.IMAGE, providerContext);

                long startedAt = System.nanoTime();
                try {
                    ImageGenerator.GeneratedImage generated = providerCallTelemetry.observe(
                            "pollinations",
                            "image_generation",
                            providerContext,
                            () -> fetchValidImage(current),
                            result -> result.providerMetadata().retryCount(),
                            this::retryCountFromFailure
                    );
                    current.storeGeneratedImage(generated.bytes(), generated.contentType().toString());
                    imageAssetRepository.saveAndFlush(current);
                    logProviderResult(current, generated, startedAt);
                    return toGeneratedAsset(current);
                } catch (ImageGenerationException exception) {
                    logProviderFailure(current, exception, startedAt);
                    return fallbackAsset();
                }
            }
        } finally {
            generationLocks.remove(assetId, lock);
        }
    }

    private ImageGenerator.GeneratedImage fetchValidImage(ImageAsset asset) {
        ImageGenerator.GenerationRequest request = new ImageGenerator.GenerationRequest(
                asset.getPrompt(),
                asset.getModel(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getSeed(),
                asset.isSafe(),
                asset.getStyleVersion()
        );
        ImageGenerator.GeneratedImage generated = imageGenerator.fetchImage(request);
        if (generated == null || generated.bytes() == null || generated.bytes().length == 0 || generated.contentType() == null) {
            throw new ImageGenerationException("이미지 provider가 유효한 이미지를 반환하지 않았습니다.");
        }
        return generated;
    }

    private int retryCountFromFailure(RuntimeException exception) {
        return exception instanceof ImageProviderFailure failure ? failure.retryCount() : 0;
    }

    private void logProviderResult(ImageAsset asset, ImageGenerator.GeneratedImage generated, long startedAt) {
        ImageGenerator.ProviderMetadata metadata = generated.providerMetadata();
        log.info(
                "image_provider_result assetId={} sessionId={} turn={} promptHash={} model={} size={}x{} seed={} styleVersion={} latencyMs={} outcome=SUCCESS status={} errorCode=- requestId={} bytes={} mime={} retryCount={}",
                asset.getId(), asset.getGameSession().getId(), asset.getTurnNumber(), promptHash(asset.getPrompt()),
                asset.getModel(), asset.getWidth(), asset.getHeight(), asset.getSeed(), asset.getStyleVersion(),
                elapsedMs(startedAt), metadata.status(), safeLog(metadata.requestId()), generated.bytes().length,
                generated.contentType(), metadata.retryCount()
        );
    }

    private void logProviderFailure(ImageAsset asset, ImageGenerationException exception, long startedAt) {
        int status = 0;
        String code = exception.getClass().getSimpleName();
        String requestId = null;
        int retryCount = 0;
        if (exception instanceof ImageProviderFailure failure) {
            status = failure.status();
            code = failure.code();
            requestId = failure.requestId();
            retryCount = failure.retryCount();
        }
        log.warn(
                "image_provider_result assetId={} sessionId={} turn={} promptHash={} model={} size={}x{} seed={} styleVersion={} latencyMs={} outcome=FAILURE status={} errorCode={} requestId={} bytes=0 mime=- retryCount={}",
                asset.getId(), asset.getGameSession().getId(), asset.getTurnNumber(), promptHash(asset.getPrompt()),
                asset.getModel(), asset.getWidth(), asset.getHeight(), asset.getSeed(), asset.getStyleVersion(),
                elapsedMs(startedAt), status, safeLog(code), safeLog(requestId), retryCount
        );
    }

    private ImageAsset findOwnedAsset(String ownerKey, String assetId) {
        return imageAssetRepository.findByIdAndGameSessionOwnerKey(assetId, ownerKey)
                .orElseThrow(() -> new ImageAssetNotFoundException("존재하지 않는 이미지 asset입니다."));
    }

    private GeneratedAsset toGeneratedAsset(ImageAsset asset) {
        return new GeneratedAsset(asset.getImageBytes().clone(), MediaType.parseMediaType(asset.getContentType()));
    }

    private GeneratedAsset fallbackAsset() {
        return new GeneratedAsset(FALLBACK_IMAGE.clone(), MediaType.parseMediaType("image/svg+xml"));
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }

    private String promptHash(String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(prompt.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String safeLog(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]", "_");
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    public record AssetReference(
            String id,
            String publicUrl,
            String prompt,
            String aspectRatio,
            String model,
            int width,
            int height,
            int seed,
            boolean safe,
            String styleVersion
    ) {}

    public record GeneratedAsset(byte[] bytes, MediaType contentType) {}
}
