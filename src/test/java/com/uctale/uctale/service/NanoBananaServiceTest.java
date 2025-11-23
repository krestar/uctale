package com.uctale.uctale.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(NanoBananaService.class)
class NanoBananaServiceTest {

    @Autowired
    private NanoBananaService nanoBananaService;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    @DisplayName("이미지 생성 요청 시, Base64 응답을 Data URL 형식으로 변환해야 한다")
    void generateImage_Success() {
        // given
        String prompt = "Test Prompt";
        String aspectRatio = "16:9";

        // [수정됨] 최신 Gemini API 응답 구조 (inline_data)
        String mockApiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "inline_data": {
                          "mime_type": "image/jpeg",
                          "data": "SGVsbG8="
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """;

        // when
        // URL 검증: gemini-2.5-flash-image 모델명 포함 여부 확인
        mockServer.expect(requestTo(containsString("gemini-2.5-flash-image")))
                .andRespond(withSuccess(mockApiResponse, MediaType.APPLICATION_JSON));

        String resultUrl = nanoBananaService.generateImage(prompt, aspectRatio);

        // then
        // 결과가 jpeg 헤더로 잘 나오는지 확인
        assertThat(resultUrl).isEqualTo("data:image/jpeg;base64,SGVsbG8=");
    }
}