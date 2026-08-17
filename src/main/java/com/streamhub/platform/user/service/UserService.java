package com.streamhub.platform.user.service;

import com.streamhub.platform.common.exception.ResourceNotFoundException;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    public User findByUid(UUID uid) {
        return userRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uid));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email));
    }

    public void updateSubscriptionFlag(Long userId, boolean isSubscribed) {
        User user = findById(userId);
        user.setSubscribed(isSubscribed);
        userRepository.save(user);
    }

    public void updateSubscriptionDetails(Long userId, String tier, LocalDateTime expiry) {
        User user = findById(userId);
        user.setSubscribed(true);
        user.setSubscriptionTier(tier);
        user.setSubscriptionExpiry(expiry);
        userRepository.save(user);
    }

    public void clearSubscription(Long userId) {
        User user = findById(userId);
        user.setSubscribed(false);
        userRepository.save(user);
    }

    /**
     * Resolves the currently authenticated user directly from the Spring
     * Security context (populated by the JWT filter). This replaces the
     * NestJS pattern of manually re-decoding the Authorization header in
     * every controller/service that needed the current user.
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof com.streamhub.platform.common.security.UserPrincipal principal)) {
            throw new com.streamhub.platform.common.exception.UnauthorizedException("Authentication required");
        }
        return principal.getUser();
    }

    public java.util.Optional<User> getCurrentUserOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof com.streamhub.platform.common.security.UserPrincipal principal)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(principal.getUser());
    }
}
