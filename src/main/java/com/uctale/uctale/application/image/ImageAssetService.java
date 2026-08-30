package com.uctale.uctale.application.image;

import com.uctale.uctale.application.cost.CostOperation;
import com.uctale.uctale.application.cost.CostRateLimiter;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.cost.ProviderCallTelemetry;
import com.uctale.uctale.domain.ImageAsset;
import com.uctale.uctale.repository.ImageAssetRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImageAssetService {

    private final ImageAssetRepository imageAssetRepository;
    private final ImageGenerator imageGenerator;
    private final CostRateLimiter costRateLimiter;
    private final ProviderCallTelemetry providerCallTelemetry;
    private final ConcurrentHashMap<String, Object> generationLocks = new ConcurrentHashMap<>();

    public ImageAssetService(
            ImageAssetRepository imageAssetRepository,
            ImageGenerator imageGenerator,
            CostRateLimiter costRateLimiter,
            ProviderCallTelemetry providerCallTelemetry
    ) {
        this.imageAssetRepository = imageAssetRepository;
        this.imageGenerator = imageGenerator;
        this.costRateLimiter = costRateLimiter;
        this.providerCallTelemetry = providerCallTelemetry;
    }

    public AssetReference issue(String prompt, String aspectRatio) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("이미지 prompt는 필수입니다.");
        }
        String normalizedAspectRatio = "1:1".equals(aspectRatio) ? "1:1" : "16:9";
        String id = UUID.randomUUID().toString();
        return new AssetReference(id, "/api/game/image-assets/" + id, prompt, normalizedAspectRatio);
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

                ImageGenerator.GeneratedImage generated = providerCallTelemetry.observe(
                        "pollinations", "image_generation", providerContext, 0,
                        () -> imageGenerator.fetchImage(current.getPrompt(), current.getAspectRatio())
                );
                if (generated == null || generated.bytes() == null || generated.bytes().length == 0) {
                    throw new ImageGenerationException("이미지 provider가 유효한 이미지를 반환하지 않았습니다.");
                }

                MediaType contentType = generated.contentType() == null ? MediaType.IMAGE_JPEG : generated.contentType();
                current.storeGeneratedImage(generated.bytes(), contentType.toString());
                imageAssetRepository.saveAndFlush(current);
                return toGeneratedAsset(current);
            }
        } finally {
            generationLocks.remove(assetId, lock);
        }
    }

    private ImageAsset findOwnedAsset(String ownerKey, String assetId) {
        return imageAssetRepository.findByIdAndGameSessionOwnerKey(assetId, ownerKey)
                .orElseThrow(() -> new ImageAssetNotFoundException("존재하지 않는 이미지 asset입니다."));
    }

    private GeneratedAsset toGeneratedAsset(ImageAsset asset) {
        return new GeneratedAsset(asset.getImageBytes().clone(), MediaType.parseMediaType(asset.getContentType()));
    }

    public record AssetReference(String id, String publicUrl, String prompt, String aspectRatio) {}

    public record GeneratedAsset(byte[] bytes, MediaType contentType) {}
}
