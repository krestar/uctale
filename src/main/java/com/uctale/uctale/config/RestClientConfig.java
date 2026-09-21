package com.uctale.uctale.config;

import com.uctale.uctale.application.narrative.NarrativeExecutionPolicy;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer restClientCustomizer(NarrativeExecutionPolicy policy) {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(policy.connectTimeoutMillis()));
            factory.setReadTimeout(Duration.ofMillis(policy.readTimeoutMillis()));

            builder.requestFactory(factory);
        };
    }
}
