package com.streamhub.platform.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The User entity intentionally does NOT extend {@code BaseEntity}.
 * <p>
 * It has its own identity shape:
 *  - {@code id}    - internal numeric primary key (fast joins/indexes, never exposed).
 *  - {@code uid}   - public-facing random UUID used anywhere a user identifier
 *                    appears in a URL or response, so raw sequential ids are
 *                    never leaked to clients.
 *  - {@code username} - unique handle.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Builder.Default
    @Column(name = "uid", nullable = false, updatable = false, unique = true, columnDefinition = "uuid")
    private UUID uid = UUID.randomUUID();

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @JsonIgnore
    @Column(name = "password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 32)
    @Builder.Default
    private RoleType roleType = RoleType.NORMAL_USER;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(name = "is_subscribed", nullable = false)
    private boolean subscribed = false;

    @Column(name = "subscription_tier", length = 32)
    private String subscriptionTier;

    @Column(name = "subscription_expiry")
    private LocalDateTime subscriptionExpiry;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean hasActiveSubscription() {
        return subscribed && subscriptionExpiry != null && subscriptionExpiry.isAfter(LocalDateTime.now());
    }
}
