package com.uctale.uctale.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRequestBodySizeFilterTest {

    @Test
    @DisplayName("JSON request body가 byte 상한을 넘으면 controller 이전 filter에서 413으로 거부한다")
    void oversizedJsonBody_IsRejected() throws ServletException, IOException {
        JsonRequestBodySizeFilter filter = new JsonRequestBodySizeFilter(16);
        MockHttpServletRequest request = jsonRequest("{\"value\":\"0123456789\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_BODY_TOO_LARGE");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("상한 이하 JSON body는 downstream에서 동일한 본문을 다시 읽을 수 있다")
    void allowedJsonBody_IsReplayableDownstream() throws ServletException, IOException {
        JsonRequestBodySizeFilter filter = new JsonRequestBodySizeFilter(64);
        String body = "{\"value\":\"ok\"}";
        MockHttpServletRequest request = jsonRequest(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(new String(
                chain.getRequest().getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        )).isEqualTo(body);
    }

    @Test
    @DisplayName("JSON이 아닌 요청은 body size filter 대상이 아니다")
    void nonJsonRequest_IsNotFiltered() throws ServletException, IOException {
        JsonRequestBodySizeFilter filter = new JsonRequestBodySizeFilter(4);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/game/progress");
        request.setContentType(MediaType.TEXT_PLAIN_VALUE);
        request.setContent("oversized".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/game/progress");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
