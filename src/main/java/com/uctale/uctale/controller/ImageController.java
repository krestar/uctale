package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.ClientIpResolver;
import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.image.ImageAssetService;
import com.uctale.uctale.security.AccessSessionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class ImageController {

    private final ImageAssetService imageAssetService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/image-assets/{assetId}")
    public ResponseEntity<byte[]> image(
            @RequestAttribute(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE) String ownerKey,
            @PathVariable String assetId,
            HttpServletRequest servletRequest
    ) {
        CostRequestContext context = CostRequestContext.create(ownerKey, clientIpResolver.resolve(servletRequest), null, null);
        return imageResponse(imageAssetService.getOrGenerate(context, assetId));
    }

    @GetMapping("/image-assets/{assetId}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(
            @RequestAttribute(AccessSessionInterceptor.OWNER_KEY_ATTRIBUTE) String ownerKey,
            @PathVariable String assetId
    ) {
        Optional<ImageAssetService.GeneratedAsset> asset = imageAssetService.getGenerated(ownerKey, assetId);
        if (asset.isEmpty()) {
            return ResponseEntity.noContent()
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        return imageResponse(asset.get());
    }

    private ResponseEntity<byte[]> imageResponse(ImageAssetService.GeneratedAsset asset) {
        return ResponseEntity.ok()
                .contentType(asset.contentType())
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .body(asset.bytes());
    }
}
