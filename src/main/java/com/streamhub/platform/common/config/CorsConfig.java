package com.streamhub.platform.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

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
        // Headers exposed to frontend JavaScript.
        // "Set-Cookie" is exposed for transparency/debugging, but
        // the browser handles HttpOnly cookies automatically.
        // "Authorization" is exposed for the access token header.
        // ---------------------------------------------------------
        configuration.setExposedHeaders(List.of(
                "Authorization",
                "Set-Cookie"
        ));

        // ---------------------------------------------------------
        // Allow credentials
        // CRITICAL: Required for HttpOnly refresh token cookies.
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