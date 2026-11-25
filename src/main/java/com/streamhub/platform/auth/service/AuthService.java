package com.streamhub.platform.auth.service;

import com.streamhub.platform.auth.entity.RefreshToken;
import com.streamhub.platform.auth.repository.RefreshTokenRepository;
import com.streamhub.platform.common.exception.BadRequestException;
import com.streamhub.platform.common.exception.UnauthorizedException;
import com.streamhub.platform.user.dto.AuthResponse;
import com.streamhub.platform.user.dto.LoginRequest;
import com.streamhub.platform.user.dto.RegisterRequest;
import com.streamhub.platform.user.dto.UserResponse;
import com.streamhub.platform.user.entity.RoleType;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.repository.UserRepository;
import com.streamhub.platform.common.security.JwtService;
import com.streamhub.platform.analytics.service.AnalyticsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AnalyticsService analyticsService;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Returned to the controller so it can do two things:
     *  1. Send the AuthResponse (access token + user) in the JSON body
     *  2. Set the raw refresh token string as an HttpOnly cookie
     */
    public record LoginResult(AuthResponse authResponse, String refreshToken) {}

    // -----------------------------------------------------------------
    // REGISTER
    // -----------------------------------------------------------------
    @Transactional
    public LoginResult register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("A user with this email already exists");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("This username is already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roleType(RoleType.NORMAL_USER)
                .active(true)
                .build();
        user = userRepository.save(user);

        analyticsService.recordRegistrationVisit(user, httpRequest);

        String accessToken = jwtService.generateToken(
                user.getId(), user.getEmail(), user.getRoleType().name());

        RefreshToken refreshToken = createRefreshToken(user.getUid());

        AuthResponse authResponse = AuthResponse.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();

        return new LoginResult(authResponse, refreshToken.getToken());
    }

    // -----------------------------------------------------------------
    // LOGIN
    // -----------------------------------------------------------------
    @Transactional
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new UnauthorizedException("This account has been deactivated");
        }

        String accessToken = jwtService.generateToken(
                user.getId(), user.getEmail(), user.getRoleType().name());

        RefreshToken refreshToken = createRefreshToken(user.getUid());

        AuthResponse authResponse = AuthResponse.builder()
                .token(accessToken)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();

        return new LoginResult(authResponse, refreshToken.getToken());
    }

    // -----------------------------------------------------------------
    // REFRESH TOKEN — CREATE (signed JWT, not UUID)
    // -----------------------------------------------------------------
    @Transactional
    public RefreshToken createRefreshToken(UUID userUid) {
        User user = userRepository.findByUid(userUid)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Generate a cryptographically signed JWT (base64url encoded)
        String tokenValue = jwtService.generateRefreshToken(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    // -----------------------------------------------------------------
    // REFRESH TOKEN — VERIFY & ROTATE
    // -----------------------------------------------------------------
    @Transactional
    public RefreshToken verifyAndRotateRefreshToken(String token) {
        // 1. Stateless check: verify the JWT signature + expiry first.
        //    This rejects forged/tampered tokens without touching the DB.
        Claims claims;
        try {
            claims = jwtService.parseRefreshToken(token);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new UnauthorizedException("Refresh token expired");
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        // 2. Stateful check: look it up in the DB to verify it hasn't been revoked.
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not recognized"));

        if (refreshToken.isRevoked()) {
            // Token theft detection: someone is replaying a revoked token.
            // Revoke ALL active sessions for this user as a precaution.
            refreshTokenRepository.revokeAllUserTokens(refreshToken.getUser());
            throw new UnauthorizedException("Refresh token was revoked. Please login again.");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException("Refresh token expired. Please login again.");
        }

        // 3. Rotate: revoke the current token, issue a new one.
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        Long userId = Long.valueOf(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return createRefreshToken(user.getUid());
    }
}