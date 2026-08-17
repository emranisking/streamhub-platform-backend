package com.streamhub.platform.watchhistory.entity;

import com.streamhub.platform.common.entity.BaseEntity;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.video.entity.Video;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "watch_history", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "video_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WatchHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "watched_at", nullable = false)
    private LocalDateTime watchedAt;
}
