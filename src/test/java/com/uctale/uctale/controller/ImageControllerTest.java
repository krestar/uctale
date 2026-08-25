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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock
    private ImageGenerator imageGenerator;

    @Test
    @DisplayName("빈 이미지 prompt는 provider 호출 전에 거부한다")
    void image_RejectsBlankPromptBeforeProviderCall() {
        ImageController controller = new ImageController(imageGenerator);

        assertThatThrownBy(() -> controller.image(" ", "16:9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미지 prompt는 필수입니다.");

        verify(imageGenerator, never()).fetchImage(" ", "16:9");
    }

    @Test
    @DisplayName("지원하지 않는 이미지 비율은 provider 호출 전에 거부한다")
    void image_RejectsUnsupportedAspectRatioBeforeProviderCall() {
        ImageController controller = new ImageController(imageGenerator);

        assertThatThrownBy(() -> controller.image("zombie", "4:3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 이미지 비율입니다.");

        verify(imageGenerator, never()).fetchImage("zombie", "4:3");
    }

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
    @DisplayName("이미지 provider 성공 시 private cache 이미지 응답을 반환한다")
    void image_ReturnsGeneratedImageWithPrivateCache() {
        ImageController controller = new ImageController(imageGenerator);
        byte[] bytes = new byte[]{1, 2, 3};
        given(imageGenerator.fetchImage("zombie", "16:9"))
                .willReturn(new ImageGenerator.GeneratedImage(bytes, MediaType.IMAGE_PNG));

        ResponseEntity<byte[]> response = controller.image("zombie", "16:9");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getCacheControl()).contains("private");
        assertThat(response.getBody()).containsExactly(bytes);
    }
}
