package com.streamhub.platform.watchhistory.dto;

import com.streamhub.platform.video.dto.VideoSummaryResponse;
import com.streamhub.platform.watchhistory.entity.WatchHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class WatchHistoryResponse {
    private UUID id;
    private VideoSummaryResponse video;
    private LocalDateTime watchedAt;

    public static WatchHistoryResponse from(WatchHistory history) {
        return WatchHistoryResponse.builder()
                .id(history.getId())
                .video(VideoSummaryResponse.from(history.getVideo()))
                .watchedAt(history.getWatchedAt())
                .build();
    }
}
