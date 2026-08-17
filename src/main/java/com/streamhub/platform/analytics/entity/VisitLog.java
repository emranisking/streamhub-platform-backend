package com.streamhub.platform.analytics.entity;

import com.streamhub.platform.common.entity.BaseEntity;
import com.streamhub.platform.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * One row per recorded visit. Written either by the public
 * `POST /analytics/track` endpoint (called once per app load / session by
 * the frontend - deliberately NOT on every API call, to keep the analytics
 * signal meaningful) or automatically on registration.
 */
@Entity
@Table(name = "visit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class VisitLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "visited_at", nullable = false)
    private LocalDateTime visitedAt;

    @Builder.Default
    @Column(name = "new_registration", nullable = false)
    private boolean newRegistration = false;
}
