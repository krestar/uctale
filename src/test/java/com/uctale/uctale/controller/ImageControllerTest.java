package com.uctale.uctale.controller;

import com.uctale.uctale.application.image.ImageAssetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock
    private ImageAssetService imageAssetService;

    @Test
    @DisplayName("image asset 조회는 요청 owner와 asset ID를 application service에 전달한다")
    void image_UsesOwnedAssetLookup() {
        ImageController controller = new ImageController(imageAssetService);
        byte[] bytes = new byte[]{1, 2, 3};
        given(imageAssetService.getOrGenerate(OWNER_KEY, "asset-id"))
                .willReturn(new ImageAssetService.GeneratedAsset(bytes, MediaType.IMAGE_PNG));

        ResponseEntity<byte[]> response = controller.image(OWNER_KEY, "asset-id");

        verify(imageAssetService).getOrGenerate(OWNER_KEY, "asset-id");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getCacheControl()).contains("private");
        assertThat(response.getBody()).containsExactly(bytes);
    }
}
