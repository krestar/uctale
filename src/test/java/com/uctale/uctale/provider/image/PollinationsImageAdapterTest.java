package com.uctale.uctale.provider.image;

import com.uctale.uctale.application.image.ImageGenerationException;
import com.uctale.uctale.application.image.ImageGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PollinationsImageAdapterTest {

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    @DisplayName("secret은 Bearer header로만 전달하고 model/size/seed/safe를 명시한다")
    void requestContract_UsesBearerAndExplicitGenerationParams() {
        PollinationsImageAdapter adapter = adapter(0, 8_388_608);
        String url = url(123);
        mockServer.expect(requestTo(url))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer TEST_TOKEN"))
                .andRespond(withSuccess("image", MediaType.IMAGE_JPEG));

        ImageGenerator.GeneratedImage image = adapter.fetchImage(request(123));

        assertThat(image.contentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(url).doesNotContain("key=", "nologo");
        mockServer.verify();
    }

    @Test
    @DisplayName("429는 Retry-After를 따르며 동일 URL과 seed로 bounded retry한다")
    void rateLimited_RetriesSameRequest() {
        PollinationsImageAdapter adapter = adapter(1, 8_388_608);
        String url = url(456);
        mockServer.expect(requestTo(url))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(error(429, "RATE_LIMITED", "req-429")));
        mockServer.expect(requestTo(url))
                .andRespond(withSuccess("image", MediaType.IMAGE_PNG));

        ImageGenerator.GeneratedImage image = adapter.fetchImage(request(456));

        assertThat(image.contentType()).isEqualTo(MediaType.IMAGE_PNG);
        mockServer.verify();
    }

    @Test
    @DisplayName("502와 503은 재시도 대상이다")
    void transientProviderErrors_AreRetried() {
        PollinationsImageAdapter adapter = adapter(2, 8_388_608);
        String url = url(789);
        mockServer.expect(requestTo(url)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON).body(error(502, "BAD_GATEWAY", "req-502")));
        mockServer.expect(requestTo(url)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "0")
                .contentType(MediaType.APPLICATION_JSON).body(error(503, "SERVICE_UNAVAILABLE", "req-503")));
        mockServer.expect(requestTo(url)).andRespond(withSuccess("image", MediaType.IMAGE_JPEG));

        assertThat(adapter.fetchImage(request(789)).bytes()).isNotEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("400/401/402/403/422는 자동 재시도하지 않는다")
    void permanentErrors_AreNotRetried() {
        int[] statuses = {400, 401, 402, 403, 422};
        for (int i = 0; i < statuses.length; i++) {
            int status = statuses[i];
            int seed = 900 + i;
            mockServer.expect(requestTo(url(seed))).andRespond(withStatus(HttpStatus.valueOf(status))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error(status, "PERMANENT_" + status, "req-" + status)));
        }

        PollinationsImageAdapter adapter = adapter(2, 8_388_608);
        for (int i = 0; i < statuses.length; i++) {
            int status = statuses[i];
            int seed = 900 + i;
            assertThatThrownBy(() -> adapter.fetchImage(request(seed)))
                    .isInstanceOf(PollinationsProviderException.class)
                    .satisfies(error -> assertThat(((PollinationsProviderException) error).status()).isEqualTo(status));
        }
        mockServer.verify();
    }

    @Test
    @DisplayName("빈 body와 허용되지 않은 MIME, 최대 크기 초과 응답을 거부한다")
    void invalidResponses_AreRejected() {
        mockServer.expect(requestTo(url(1001))).andRespond(withSuccess(new byte[0], MediaType.IMAGE_JPEG));
        mockServer.expect(requestTo(url(1002))).andRespond(withSuccess("not-image", MediaType.TEXT_PLAIN));
        mockServer.expect(requestTo(url(1003))).andRespond(withSuccess("1234", MediaType.IMAGE_PNG));

        PollinationsImageAdapter normalLimitAdapter = adapter(0, 100);
        assertThatThrownBy(() -> normalLimitAdapter.fetchImage(request(1001)))
                .isInstanceOf(ImageGenerationException.class);
        assertThatThrownBy(() -> normalLimitAdapter.fetchImage(request(1002)))
                .isInstanceOf(ImageGenerationException.class);
        assertThatThrownBy(() -> adapter(0, 3).fetchImage(request(1003)))
                .isInstanceOf(ImageGenerationException.class);

        mockServer.verify();
    }

    private PollinationsImageAdapter adapter(int maxRetries, int maxBytes) {
        return new PollinationsImageAdapter(
                new ObjectMapper(), builder.build(), "TEST_TOKEN", maxRetries, 0, maxBytes, millis -> {}
        );
    }

    private ImageGenerator.GenerationRequest request(int seed) {
        return new ImageGenerator.GenerationRequest(
                "castle gate", "flux", 1024, 576, seed, true, "uctale-charcoal-v1"
        );
    }

    private String url(int seed) {
        return "https://gen.pollinations.ai/image/castle%20gate?model=flux&width=1024&height=576&seed="
                + seed + "&safe=true";
    }

    private String error(int status, String code, String requestId) {
        return "{\"status\":" + status + ",\"success\":false,\"error\":{\"code\":\"" + code
                + "\",\"message\":\"failed\",\"requestId\":\"" + requestId + "\"}}";
    }
}
