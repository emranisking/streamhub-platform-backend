package com.streamhub.platform.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Single, global CORS configuration driven by `app.cors.frontend-url`.
 *
 * Every controller in the application shares this same policy.
 * There is intentionally no per-controller CORS configuration.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.frontend-url}")
    private String frontendUrl;

    @Bean
    public CorsFilter corsFilter() {

        CorsConfiguration configuration = new CorsConfiguration();

        // ---------------------------------------------------------
        // Allowed frontend origins
        // Supports comma-separated origins:
        // http://localhost:5173,http://localhost:3000
        // ---------------------------------------------------------
        List<String> origins = Arrays.stream(frontendUrl.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        configuration.setAllowedOrigins(origins);

        // ---------------------------------------------------------
        // Allowed HTTP methods
        // ---------------------------------------------------------
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        // ---------------------------------------------------------
        // Allowed request headers
        // ---------------------------------------------------------
        configuration.setAllowedHeaders(List.of("*"));

        // ---------------------------------------------------------
        // Headers exposed to frontend JavaScript
        // ---------------------------------------------------------
        configuration.setExposedHeaders(List.of(
                "Authorization"
        ));

        // ---------------------------------------------------------
        // Allow credentials
        // Required if frontend sends credentials/cookies.
        // Also compatible with Authorization headers.
        // ---------------------------------------------------------
        configuration.setAllowCredentials(true);

        // ---------------------------------------------------------
        // Browser caches preflight response for 1 hour
        // ---------------------------------------------------------
        configuration.setMaxAge(3600L);

        // ---------------------------------------------------------
        // Apply globally
        // ---------------------------------------------------------
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return new CorsFilter(source);
    }
}