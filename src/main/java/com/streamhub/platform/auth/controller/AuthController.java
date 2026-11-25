package com.streamhub.platform.auth.controller;

import com.streamhub.platform.auth.entity.RefreshToken;
import com.streamhub.platform.auth.repository.RefreshTokenRepository;
import com.streamhub.platform.auth.service.AuthService;
import com.streamhub.platform.common.exception.UnauthorizedException;
import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.common.security.JwtService;
import com.streamhub.platform.user.dto.AuthResponse;
import com.streamhub.platform.user.dto.LoginRequest;
import com.streamhub.platform.user.dto.RegisterRequest;
import com.streamhub.platform.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    private void attachRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(false) // Set to TRUE in production (requires HTTPS)
                .sameSite("Lax") // Set to "None" in production if domains differ
                .path("/api/v1/auth") // Only sent to auth endpoints
                .maxAge(7 * 24 * 60 * 60) // 7 days
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        AuthService.LoginResult result = authService.register(request, httpRequest);

        attachRefreshCookie(response, result.refreshToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "Account created successfully",
                        result.authResponse()
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthService.LoginResult result = authService.login(request);

        attachRefreshCookie(response, result.refreshToken());

        return ResponseEntity.ok(
                ApiResponse.ok("Login successful", result.authResponse())
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken == null) {
            throw new UnauthorizedException("Refresh token is missing");
        }

        RefreshToken newRefreshToken = authService.verifyAndRotateRefreshToken(refreshToken);

        // Generate new Access Token (JWT)
        String newAccessToken = jwtService.generateToken(
                newRefreshToken.getUser().getId(),
                newRefreshToken.getUser().getEmail(),
                newRefreshToken.getUser().getRoleType().name()
        );

        // Attach the new rotated Refresh Token cookie
        attachRefreshCookie(response, newRefreshToken.getToken());

        AuthResponse authResponse = AuthResponse.builder()
                .token(newAccessToken)
                .tokenType("Bearer")
                .user(UserResponse.from(newRefreshToken.getUser()))
                .build();

        return ResponseEntity.ok(ApiResponse.ok(authResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken != null) {
            // Delete from DB
            refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }

        // Clear cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/api/v1/auth")
                .maxAge(0) // Delete immediately
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(ApiResponse.message("Logged out successfully"));
    }
}
