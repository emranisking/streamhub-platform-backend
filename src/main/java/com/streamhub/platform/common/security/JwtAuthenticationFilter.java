package com.streamhub.platform.common.security;

import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Verifies the Authorization header on every request (when present) and
 * populates the Spring Security context. Authentication here is always a
 * strict verify (never a "decode without verify" shortcut - fixes the
 * original PlaylistController bug where expired tokens were still
 * accepted). Requests without a token, or with an invalid one, simply
 * proceed unauthenticated - individual endpoints decide via
 * SecurityConfig / @PreAuthorize whether authentication is required.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtService.isValid(token)) {
            Long userId = jwtService.extractUserId(token);
            Optional<User> user = userRepository.findById(userId);
            if (user.isPresent() && user.get().isActive()) {
                UserPrincipal principal = new UserPrincipal(user.get());
                var authToken = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
