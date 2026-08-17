package com.streamhub.platform.video.dto;

import com.streamhub.platform.video.entity.Video;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lightweight shape used by list endpoints (GET /videos). */
@Getter
@Builder
public class VideoSummaryResponse {
    private UUID id;
    private String title;
    private String thumbnailUrl;
    private String videoUrl;
    private long views;
    private long likes;
    private String categoryName;
    private LocalDateTime createdAt;

    public static VideoSummaryResponse from(Video video) {
        return VideoSummaryResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .thumbnailUrl(video.getThumbnailUrl())
                .videoUrl(video.getVideoUrl())
                .views(video.getViews())
                .likes(video.getLikes())
                .categoryName(video.getCategory() != null ? video.getCategory().getName() : null)
                .createdAt(video.getCreatedAt())
                .build();
    }
}
