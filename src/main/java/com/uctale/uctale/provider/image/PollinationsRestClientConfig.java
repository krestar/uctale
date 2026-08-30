package com.uctale.uctale.provider.image;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class PollinationsRestClientConfig {

    @Bean
    @Qualifier("pollinationsRestClient")
    RestClient pollinationsRestClient(
            RestClient.Builder builder,
            @Value("${game.image.connect-timeout-ms:10000}") long connectTimeoutMs,
            @Value("${game.image.read-timeout-ms:120000}") long readTimeoutMs
    ) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Pollinations timeout은 1ms 이상이어야 합니다.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return builder.requestFactory(requestFactory).build();
    }
}
