package com.uctale.uctale.application.image;

import com.uctale.uctale.domain.ImageAsset;
import com.uctale.uctale.repository.ImageAssetRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImageAssetService {

    private final ImageAssetRepository imageAssetRepository;
    private final ImageGenerator imageGenerator;
    private final ConcurrentHashMap<String, Object> generationLocks = new ConcurrentHashMap<>();

    public ImageAssetService(ImageAssetRepository imageAssetRepository, ImageGenerator imageGenerator) {
        this.imageAssetRepository = imageAssetRepository;
        this.imageGenerator = imageGenerator;
    }

    public GeneratedAsset getOrGenerate(String ownerKey, String assetId) {
        ImageAsset asset = findOwnedAsset(ownerKey, assetId);
        if (asset.generated()) {
            return toGeneratedAsset(asset);
        }

        Object lock = generationLocks.computeIfAbsent(assetId, ignored -> new Object());
        try {
            synchronized (lock) {
                ImageAsset current = findOwnedAsset(ownerKey, assetId);
                if (current.generated()) {
                    return toGeneratedAsset(current);
                }

                ImageGenerator.GeneratedImage generated = imageGenerator.fetchImage(
                        current.getPrompt(),
                        current.getAspectRatio()
                );
                if (generated == null || generated.bytes() == null || generated.bytes().length == 0) {
                    throw new ImageGenerationException("이미지 provider가 유효한 이미지를 반환하지 않았습니다.");
                }

                MediaType contentType = generated.contentType() == null
                        ? MediaType.IMAGE_JPEG
                        : generated.contentType();
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
        return new GeneratedAsset(
                asset.getImageBytes().clone(),
                MediaType.parseMediaType(asset.getContentType())
        );
    }

    public record GeneratedAsset(byte[] bytes, MediaType contentType) {}
}
