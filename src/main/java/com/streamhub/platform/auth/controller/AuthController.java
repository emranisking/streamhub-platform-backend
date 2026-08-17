package com.streamhub.platform.auth.controller;

import com.streamhub.platform.auth.service.AuthService;
import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.user.dto.AuthResponse;
import com.streamhub.platform.user.dto.LoginRequest;
import com.streamhub.platform.user.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Registration and login. Both endpoints are public.
 * Storyline: this is Alice's on-ramp from guest to registered user, and
 * Bob/Carol's entry point for every session.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account", description = "Creates a new NORMAL_USER account and returns a JWT.")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request,
                                                                HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Account created successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticates an existing user and returns a JWT.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }
}
