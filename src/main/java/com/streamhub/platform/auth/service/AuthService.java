package com.streamhub.platform.auth.service;

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
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles registration and login. This is the only place in the application
 * that issues JWTs, using the single shared {@link JwtService} (fixes the
 * original NestJS bug of two JwtService instances with different expiries).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AnalyticsService analyticsService;

    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
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

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRoleType().name());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new UnauthorizedException("This account has been deactivated");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRoleType().name());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }
}
