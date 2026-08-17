package com.streamhub.platform.user.dto;

import com.streamhub.platform.user.entity.RoleType;
import com.streamhub.platform.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {
    private UUID uid;
    private String username;
    private String email;
    private RoleType roleType;
    private boolean subscribed;
    private String subscriptionTier;
    private LocalDateTime subscriptionExpiry;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .uid(user.getUid())
                .username(user.getUsername())
                .email(user.getEmail())
                .roleType(user.getRoleType())
                .subscribed(user.isSubscribed())
                .subscriptionTier(user.getSubscriptionTier())
                .subscriptionExpiry(user.getSubscriptionExpiry())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
