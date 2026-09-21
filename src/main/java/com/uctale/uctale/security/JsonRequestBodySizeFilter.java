package com.uctale.uctale.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class JsonRequestBodySizeFilter extends OncePerRequestFilter {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final String ERROR_BODY =
            "{\"code\":\"REQUEST_BODY_TOO_LARGE\",\"message\":\"JSON 요청 본문이 너무 큽니다.\"}";

    private final int maxJsonBodyBytes;

    public JsonRequestBodySizeFilter(long maxJsonBodyBytes) {
        if (maxJsonBodyBytes <= 0 || maxJsonBodyBytes > Integer.MAX_VALUE - 1L) {
            throw new IllegalArgumentException("game.request.max-json-body-bytes는 1 이상 Integer.MAX_VALUE 미만이어야 합니다.");
        }
        this.maxJsonBodyBytes = (int) maxJsonBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BODY_METHODS.contains(request.getMethod())
                || !isJsonContentType(request.getContentType());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxJsonBodyBytes) {
            reject(response);
            return;
        }

        ReadResult readResult = readBody(request);
        if (readResult.tooLarge()) {
            reject(response);
            return;
        }

        filterChain.doFilter(new CachedBodyRequest(request, readResult.body()), response);
    }

    private ReadResult readBody(HttpServletRequest request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxJsonBodyBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;

        while ((read = request.getInputStream().read(buffer)) != -1) {
            if (total > maxJsonBodyBytes - read) {
                return new ReadResult(new byte[0], true);
            }
            output.write(buffer, 0, read);
            total += read;
        }
        return new ReadResult(output.toByteArray(), false);
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return MediaType.APPLICATION_JSON_VALUE.equals(mediaType)
                || (mediaType.startsWith("application/") && mediaType.endsWith("+json"));
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(ERROR_BODY);
    }

    private record ReadResult(byte[] body, boolean tooLarge) {}

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("비동기 request body 읽기는 지원하지 않습니다.");
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
