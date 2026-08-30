package com.uctale.uctale.controller;

import com.uctale.uctale.application.cost.CostRequestContext;
import com.uctale.uctale.application.image.ImageAssetService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    private static final String OWNER_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Mock private ImageAssetService imageAssetService;
    @Mock private HttpServletRequest servletRequest;

    @Test
    @DisplayName("image asset 조회는 owner와 client IP를 비용 요청 context에 포함한다")
    void image_UsesOwnedAssetLookup() {
        ImageController controller = new ImageController(imageAssetService);
        byte[] bytes = new byte[]{1, 2, 3};
        given(servletRequest.getRemoteAddr()).willReturn("1.2.3.4");
        given(imageAssetService.getOrGenerate(any(CostRequestContext.class), eq("asset-id")))
                .willReturn(new ImageAssetService.GeneratedAsset(bytes, MediaType.IMAGE_PNG));

        ResponseEntity<byte[]> response = controller.image(OWNER_KEY, "asset-id", servletRequest);

        ArgumentCaptor<CostRequestContext> contextCaptor = ArgumentCaptor.forClass(CostRequestContext.class);
        verify(imageAssetService).getOrGenerate(contextCaptor.capture(), eq("asset-id"));
        assertThat(contextCaptor.getValue().ownerKey()).isEqualTo(OWNER_KEY);
        assertThat(contextCaptor.getValue().clientIp()).isEqualTo("1.2.3.4");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getCacheControl()).contains("private");
        assertThat(response.getBody()).containsExactly(bytes);
    }
}
