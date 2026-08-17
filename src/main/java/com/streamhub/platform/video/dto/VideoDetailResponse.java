package com.streamhub.platform.video.dto;

import com.streamhub.platform.video.entity.Video;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Full shape used by GET /videos/{id}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoDetailResponse {
    private UUID id;
    private String title;
    private String description;
    private String videoUrl;
    private String thumbnailUrl;
    private Integer durationSeconds;
    private long views;
    private long likes;
    private UUID categoryId;
    private String categoryName;
    private LocalDateTime createdAt;

    public static VideoDetailResponse from(Video video) {
        return VideoDetailResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .description(video.getDescription())
                .videoUrl(video.getVideoUrl())
                .thumbnailUrl(video.getThumbnailUrl())
                .durationSeconds(video.getDurationSeconds())
                .views(video.getViews())
                .likes(video.getLikes())
                .categoryId(video.getCategory() != null ? video.getCategory().getId() : null)
                .categoryName(video.getCategory() != null ? video.getCategory().getName() : null)
                .createdAt(video.getCreatedAt())
                .build();
    }
}
