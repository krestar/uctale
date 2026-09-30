package com.uctale.uctale.provider.image;

import com.uctale.uctale.application.image.ImageGenerationException;
import com.uctale.uctale.application.image.ImageGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;

@Slf4j
@Component
public class PollinationsImageAdapter implements ImageGenerator {

    private static final String PROVIDER_BASE_URL = "https://gen.pollinations.ai/image";
    private static final MediaType IMAGE_PNG = MediaType.IMAGE_PNG;
    private static final MediaType IMAGE_JPEG = MediaType.IMAGE_JPEG;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String pollinationsToken;
    private final int maxRetries;
    private final long retryBaseDelayMs;
    private final long maxRetryDelayMs;
    private final int maxImageBytes;
    private final int maxErrorBodyBytes;
    private final Sleeper sleeper;

    @Autowired
    public PollinationsImageAdapter(
            ObjectMapper objectMapper,
            @Qualifier("pollinationsRestClient") RestClient restClient,
            @Value("${pollinations.token}") String pollinationsToken,
            @Value("${game.image.max-retries:2}") int maxRetries,
            @Value("${game.image.retry-base-delay-ms:250}") long retryBaseDelayMs,
            @Value("${game.image.max-retry-delay-ms:2000}") long maxRetryDelayMs,
            @Value("${game.image.max-response-bytes:8388608}") int maxImageBytes,
            @Value("${game.image.max-error-body-bytes:65536}") int maxErrorBodyBytes
    ) {
        this(
                objectMapper, restClient, pollinationsToken, maxRetries, retryBaseDelayMs, maxRetryDelayMs,
                maxImageBytes, maxErrorBodyBytes, Thread::sleep
        );
    }

    PollinationsImageAdapter(
            ObjectMapper objectMapper,
            RestClient restClient,
            String pollinationsToken,
            int maxRetries,
            long retryBaseDelayMs,
            long maxRetryDelayMs,
            int maxImageBytes,
            int maxErrorBodyBytes,
            Sleeper sleeper
    ) {
        if (pollinationsToken == null || pollinationsToken.isBlank()) {
            throw new IllegalArgumentException("Pollinations token은 필수입니다.");
        }
        if (maxRetries < 0 || maxRetries > 5) {
            throw new IllegalArgumentException("Pollinations max retries는 0~5 범위여야 합니다.");
        }
        if (retryBaseDelayMs < 0 || maxRetryDelayMs < 0 || maxImageBytes <= 0 || maxErrorBodyBytes <= 0) {
            throw new IllegalArgumentException("Pollinations retry/body limit 설정이 올바르지 않습니다.");
        }
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.pollinationsToken = pollinationsToken;
        this.maxRetries = maxRetries;
        this.retryBaseDelayMs = retryBaseDelayMs;
        this.maxRetryDelayMs = maxRetryDelayMs;
        this.maxImageBytes = maxImageBytes;
        this.maxErrorBodyBytes = maxErrorBodyBytes;
        this.sleeper = sleeper;
    }

    @Override
    public GeneratedImage fetchImage(GenerationRequest request) {
        URI providerUri = buildProviderUri(request);
        String promptHash = promptHash(request.prompt());

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            long startedAt = System.nanoTime();
            try {
                ResponseEntity<byte[]> response = restClient.get()
                        .uri(providerUri)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + pollinationsToken)
                        .exchange((httpRequest, httpResponse) -> readResponse(httpResponse));

                GeneratedImage image = validateResponse(response, attempt);
                log.info(
                        "pollinations_image promptHash={} model={} size={}x{} seed={} safe={} styleVersion={} latencyMs={} outcome=SUCCESS status={} requestId={} mime={} bytes={} retryCount={}",
                        promptHash, request.model(), request.width(), request.height(), request.seed(), request.safe(), request.styleVersion(),
                        elapsedMs(startedAt), image.providerMetadata().status(), safeLog(image.providerMetadata().requestId()),
                        image.contentType(), image.bytes().length, image.providerMetadata().retryCount()
                );
                return image;
            } catch (PollinationsProviderException exception) {
                boolean willRetry = exception.retryable() && attempt < maxRetries;
                log.warn(
                        "pollinations_image promptHash={} model={} size={}x{} seed={} safe={} styleVersion={} latencyMs={} outcome=FAILURE status={} code={} requestId={} retryAfterSeconds={} retryCount={} willRetry={}",
                        promptHash, request.model(), request.width(), request.height(), request.seed(), request.safe(), request.styleVersion(),
                        elapsedMs(startedAt), exception.status(), safeLog(exception.code()), safeLog(exception.requestId()),
                        exception.retryAfterSeconds(), attempt, willRetry
                );
                if (!willRetry) {
                    throw exception.withRetryCount(attempt);
                }
                sleepBeforeRetry(exception.retryAfterSeconds(), attempt);
            } catch (ResourceAccessException exception) {
                boolean willRetry = attempt < maxRetries && fallbackRetryDelayWithinLimit(attempt);
                log.warn(
                        "pollinations_image promptHash={} model={} size={}x{} seed={} styleVersion={} latencyMs={} outcome=FAILURE status=0 code=NETWORK_ERROR retryCount={} willRetry={}",
                        promptHash, request.model(), request.width(), request.height(), request.seed(), request.styleVersion(),
                        elapsedMs(startedAt), attempt, willRetry
                );
                if (!willRetry) {
                    throw new PollinationsProviderException(
                            "Pollinations 네트워크 요청에 실패했습니다.", exception, true, attempt
                    );
                }
                sleepBeforeRetry(null, attempt);
            } catch (RestClientException exception) {
                log.warn(
                        "pollinations_image promptHash={} model={} size={}x{} seed={} styleVersion={} latencyMs={} outcome=FAILURE status=0 code=REST_CLIENT_ERROR retryCount={} willRetry=false",
                        promptHash, request.model(), request.width(), request.height(), request.seed(), request.styleVersion(),
                        elapsedMs(startedAt), attempt
                );
                throw new ImageGenerationException("Pollinations HTTP client 요청에 실패했습니다.", exception);
            }
        }
        throw new ImageGenerationException("Pollinations 이미지 생성에 실패했습니다.");
    }

    private ResponseEntity<byte[]> readResponse(ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().isError()) {
            throw providerException(response);
        }
        long contentLength = response.getHeaders().getContentLength();
        if (contentLength > maxImageBytes) {
            throw new ImageGenerationException("Pollinations 이미지가 최대 허용 크기를 초과했습니다.");
        }
        byte[] body = readBoundedBody(response.getBody(), maxImageBytes, true);
        return new ResponseEntity<>(body, response.getHeaders(), response.getStatusCode());
    }

    private GeneratedImage validateResponse(ResponseEntity<byte[]> response, int retryCount) {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new ImageGenerationException("Pollinations가 빈 이미지를 반환했습니다.");
        }
        MediaType contentType = response.getHeaders().getContentType();
        if (!isAllowedImageType(contentType)) {
            throw new ImageGenerationException("Pollinations 이미지 MIME type이 허용되지 않습니다.");
        }
        return new GeneratedImage(
                body,
                contentType,
                new ProviderMetadata(
                        response.getStatusCode().value(),
                        response.getHeaders().getFirst("X-Request-Id"),
                        retryCount
                )
        );
    }

    private boolean isAllowedImageType(MediaType contentType) {
        return contentType != null
                && (IMAGE_JPEG.isCompatibleWith(contentType) || IMAGE_PNG.isCompatibleWith(contentType));
    }

    private URI buildProviderUri(GenerationRequest request) {
        return UriComponentsBuilder.fromUriString(PROVIDER_BASE_URL)
                .pathSegment(request.prompt())
                .queryParam("model", request.model())
                .queryParam("width", request.width())
                .queryParam("height", request.height())
                .queryParam("seed", request.seed())
                .queryParam("safe", request.safe())
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
    }

    private PollinationsProviderException providerException(ClientHttpResponse response) {
        final int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException exception) {
            return new PollinationsProviderException(
                    "Pollinations provider 상태 코드를 읽지 못했습니다.", exception, true, 0
            );
        }

        String code = "HTTP_" + status;
        String requestId = null;
        try {
            String body = new String(
                    readBoundedBody(response.getBody(), maxErrorBodyBytes, false),
                    StandardCharsets.UTF_8
            );
            if (!body.isBlank()) {
                JsonNode root = objectMapper.readTree(body);
                JsonNode error = root.path("error");
                if (!error.path("code").isMissingNode() && !error.path("code").isNull()) {
                    code = error.path("code").asText(code);
                }
                if (!error.path("requestId").isMissingNode() && !error.path("requestId").isNull()) {
                    requestId = error.path("requestId").asText();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Preserve status even if an upstream error envelope is malformed or truncated.
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = response.getHeaders().getFirst("X-Request-Id");
        }
        Long retryAfter = parseRetryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        boolean retryableStatus = status == 429 || status == 502 || status == 503;
        boolean retryable = retryableStatus && retryAfterWithinLimit(retryAfter);
        return new PollinationsProviderException(
                "Pollinations provider 오류가 발생했습니다.", status, code, requestId, retryAfter, retryable, 0
        );
    }

    private Long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        try {
            return Math.max(0, Long.parseLong(normalized));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Math.max(0, Duration.between(Instant.now(), retryAt).toSeconds());
            } catch (DateTimeParseException ignoredDate) {
                return null;
            }
        }
    }

    private boolean retryAfterWithinLimit(Long retryAfterSeconds) {
        if (retryAfterSeconds == null) {
            return true;
        }
        if (retryAfterSeconds < 0 || retryAfterSeconds > maxRetryDelayMs / 1000L) {
            return false;
        }
        return retryAfterSeconds * 1000L <= maxRetryDelayMs;
    }

    private boolean fallbackRetryDelayWithinLimit(int attempt) {
        long multiplier = 1L << Math.min(attempt, 4);
        return retryBaseDelayMs == 0 || retryBaseDelayMs <= maxRetryDelayMs / multiplier;
    }

    private void sleepBeforeRetry(Long retryAfterSeconds, int attempt) {
        long delayMs;
        if (retryAfterSeconds != null) {
            if (!retryAfterWithinLimit(retryAfterSeconds)) {
                throw new ImageGenerationException("Pollinations Retry-After가 동기 대기 상한을 초과했습니다.");
            }
            delayMs = retryAfterSeconds * 1000L;
        } else {
            long multiplier = 1L << Math.min(attempt, 4);
            if (!fallbackRetryDelayWithinLimit(attempt)) {
                throw new ImageGenerationException("Pollinations 재시도 대기가 동기 대기 상한을 초과했습니다.");
            }
            delayMs = retryBaseDelayMs * multiplier;
        }
        if (delayMs <= 0) {
            return;
        }
        try {
            sleeper.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImageGenerationException("Pollinations 재시도 대기 중 인터럽트되었습니다.", exception);
        }
    }

    static byte[] readBoundedBody(InputStream inputStream, int maxBytes, boolean failOnOverflow) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes는 양수여야 합니다.");
        }
        byte[] body = inputStream.readNBytes(maxBytes);
        int overflowByte = inputStream.read();
        if (overflowByte == -1) {
            return body;
        }
        if (failOnOverflow) {
            throw new ImageGenerationException("Pollinations 응답이 최대 허용 크기를 초과했습니다.");
        }
        return body;
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }

    private String promptHash(String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(prompt.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String safeLog(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]", "_");
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
