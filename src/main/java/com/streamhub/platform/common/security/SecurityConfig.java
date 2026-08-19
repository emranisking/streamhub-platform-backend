package com.streamhub.platform.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Application-wide security policy.
 *
 * Coarse-grained public/private routing lives here;
 * fine-grained per-endpoint rules (e.g. ADMIN-only video creation)
 * use @PreAuthorize on controller methods themselves.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    // ---------------------------------------------------------
    // Password Encoder
    // ---------------------------------------------------------
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // ---------------------------------------------------------
    // Authentication Manager
    // ---------------------------------------------------------
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {

        return config.getAuthenticationManager();
    }

    // ---------------------------------------------------------
    // Authentication Provider
    // ---------------------------------------------------------
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();

        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    // ---------------------------------------------------------
    // Security Filter Chain
    // ---------------------------------------------------------
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {

        http

                // -----------------------------------------------------
                // CORS
                //
                // CorsConfig provides the actual CorsFilter.
                // Enabling CORS here tells Spring Security to cooperate
                // with the application's CORS configuration.
                // -----------------------------------------------------
                .cors(cors -> {})

                // -----------------------------------------------------
                // CSRF
                //
                // JWT-based stateless API does not require CSRF protection.
                // -----------------------------------------------------
                .csrf(csrf -> csrf.disable())

                // -----------------------------------------------------
                // Stateless session
                // -----------------------------------------------------
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // -----------------------------------------------------
                // Authorization rules
                // -----------------------------------------------------
                .authorizeHttpRequests(auth -> auth

                        // -------------------------------------------------
                        // CORS preflight
                        //
                        // Browser sends OPTIONS before certain API calls.
                        // It must not require JWT authentication.
                        // -------------------------------------------------
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()

                        // -------------------------------------------------
                        // Public - authentication
                        // -------------------------------------------------
                        .requestMatchers(
                                "/api/v1/auth/**"
                        ).permitAll()

                        // -------------------------------------------------
                        // Public - video browsing
                        // -------------------------------------------------
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/videos/**"
                        ).permitAll()

                        // -------------------------------------------------
                        // Public - categories
                        // -------------------------------------------------
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/categories/**"
                        ).permitAll()

                        // -------------------------------------------------
                        // Public - playback
                        // -------------------------------------------------
                        .requestMatchers(
                                "/api/v1/playback/**"
                        ).permitAll()

                        // -------------------------------------------------
                        // Public - watch limit
                        // -------------------------------------------------
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/videos/*/check-limit"
                        ).permitAll()

                        // -------------------------------------------------
                        // Public - increment watch
                        // -------------------------------------------------
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/videos/*/increment-watch"
                        ).permitAll()

                        // -------------------------------------------------
                        // Public - views
                        // -------------------------------------------------
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/videos/*/views"
                        ).permitAll()

                        // -------------------------------------------------
                        // Public - analytics tracking
                        // -------------------------------------------------
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/analytics/track"
                        ).permitAll()

                        // -------------------------------------------------
                        // Swagger / OpenAPI
                        // -------------------------------------------------
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // -------------------------------------------------
                        // Actuator
                        // -------------------------------------------------
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // -------------------------------------------------
                        // Analytics dashboard
                        // ADMIN or ANALYTIC only
                        // -------------------------------------------------
                        .requestMatchers(
                                "/api/v1/analytics/**"
                        ).hasAnyRole(
                                "ADMIN",
                                "ANALYTIC"
                        )

                        // -------------------------------------------------
                        // Everything else requires authentication
                        // -------------------------------------------------
                        .anyRequest().authenticated()
                )

                // ---------------------------------------------------------
                // Authentication provider
                // ---------------------------------------------------------
                .authenticationProvider(
                        authenticationProvider()
                )

                // ---------------------------------------------------------
                // JWT authentication filter
                // ---------------------------------------------------------
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}