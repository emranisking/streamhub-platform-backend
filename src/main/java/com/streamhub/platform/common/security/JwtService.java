package com.streamhub.platform.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Single source of truth for JWT generation/verification across the whole
 * application (fixes the original NestJS bug where AuthModule and
 * CommonModule each registered a JwtService with a different expiry).
 *
 * Two token types are issued:
 *  - ACCESS  : short-lived (default 7 days, see app.security.jwt.expiration-ms),
 *              sent in the Authorization header.
 *  - REFRESH : longer-lived (default 7 days, see refresh-expiration-ms),
 *              stored in an HttpOnly cookie, used only against /auth/refresh.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration-ms}") long expirationMs,
            @Value("${app.security.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    // -----------------------------------------------------------------
    // ACCESS TOKEN (unchanged API)
    // -----------------------------------------------------------------

    public String generateToken(Long userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // -----------------------------------------------------------------
    // REFRESH TOKEN
    // -----------------------------------------------------------------

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "REFRESH")
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Parses a refresh token and asserts that it is actually a REFRESH token.
     * Throws JwtException if the signature is invalid, expired, or if the
     * token is an access token being misused as a refresh token.
     */
    public Claims parseRefreshToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"REFRESH".equals(claims.get("type", String.class))) {
            throw new JwtException("Not a refresh token");
        }
        return claims;
    }

    public Long extractUserIdFromRefreshToken(String token) {
        return Long.valueOf(parseRefreshToken(token).getSubject());
    }

    // -----------------------------------------------------------------
    // SHARED HELPERS (unchanged API)
    // -----------------------------------------------------------------

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }
}