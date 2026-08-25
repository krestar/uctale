package com.uctale.uctale.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityWebConfig implements WebMvcConfigurer {

    private final AccessSessionInterceptor accessSessionInterceptor;

    public SecurityWebConfig(AccessSessionInterceptor accessSessionInterceptor) {
        this.accessSessionInterceptor = accessSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessSessionInterceptor)
                .addPathPatterns(
                        "/api/game/init",
                        "/api/game/progress",
                        "/api/game/image",
                        "/api/game/access-session"
                );
    }

    @Bean
    CorsFilter corsFilter(@Value("${game.cors.allowed-origins}") String configuredOrigins) {
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (origins.isEmpty() || origins.stream().anyMatch("*"::equals)) {
            throw new IllegalArgumentException("game.cors.allowed-origins는 명시적 origin allowlist여야 합니다.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return new CorsFilter(source);
    }
}
