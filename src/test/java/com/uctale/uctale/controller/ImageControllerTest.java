package com.uctale.uctale.controller;

import com.uctale.uctale.application.image.ImageGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock
    private ImageGenerator imageGenerator;

    @Test
    @DisplayName("이미지 provider 실패 시 502를 반환한다")
    void image_ReturnsBadGatewayWhenProviderFails() {
        ImageController controller = new ImageController(imageGenerator);
        given(imageGenerator.fetchImage("zombie", "16:9")).willReturn(null);

        ResponseEntity<byte[]> response = controller.image("zombie", "16:9");

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("이미지 provider 성공 시 이미지 바이트와 콘텐츠 타입을 반환한다")
    void image_ReturnsGeneratedImage() {
        ImageController controller = new ImageController(imageGenerator);
        byte[] bytes = new byte[]{1, 2, 3};
        given(imageGenerator.fetchImage("zombie", "16:9"))
                .willReturn(new ImageGenerator.GeneratedImage(bytes, MediaType.IMAGE_PNG));

        ResponseEntity<byte[]> response = controller.image("zombie", "16:9");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).containsExactly(bytes);
    }
}
