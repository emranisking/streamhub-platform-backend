package com.streamhub.platform.video.entity;

import com.streamhub.platform.category.entity.Category;
import com.streamhub.platform.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Standardized on a UUID primary key (inherited from BaseEntity) - the
 * original NestJS entity used an auto-increment numeric id here while every
 * other entity used a UUID; that inconsistency is intentionally removed in
 * this conversion.
 */
@Entity
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Video extends BaseEntity {

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Builder.Default
    @Column(name = "views", nullable = false)
    private long views = 0L;

    @Builder.Default
    @Column(name = "likes", nullable = false)
    private long likes = 0L;

    @Builder.Default
    @Column(name = "source_filename", unique = true)
    private String sourceFilename = null;
}
