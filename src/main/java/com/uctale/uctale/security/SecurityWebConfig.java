package com.uctale.uctale.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
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
                        "/api/game/image-assets/**",
                        "/api/game/access-session"
                );
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration(
            @Value("${game.cors.allowed-origins}") String configuredOrigins
    ) {
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
        configuration.setAllowedHeaders(List.of("Content-Type", AccessSessionInterceptor.CLIENT_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        CorsFilter corsFilter = new CorsFilter(source) {
            @Override
            protected boolean shouldNotFilterAsyncDispatch() {
                return false;
            }

            @Override
            protected boolean shouldNotFilterErrorDispatch() {
                return false;
            }
        };

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(corsFilter);
        registration.setUrlPatterns(List.of("/api/*"));
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
