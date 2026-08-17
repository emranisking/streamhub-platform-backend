package com.streamhub.platform.playlist.dto;

import com.streamhub.platform.playlist.entity.Playlist;
import com.streamhub.platform.video.dto.VideoSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PlaylistResponse {
    private UUID id;
    private String title;
    private LocalDateTime createdAt;
    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private UUID id;
        private int position;
        private VideoSummaryResponse video;
    }

    public static PlaylistResponse from(Playlist playlist) {
        return PlaylistResponse.builder()
                .id(playlist.getId())
                .title(playlist.getTitle())
                .createdAt(playlist.getCreatedAt())
                .items(playlist.getItems().stream()
                        .map(item -> Item.builder()
                                .id(item.getId())
                                .position(item.getPosition())
                                .video(VideoSummaryResponse.from(item.getVideo()))
                                .build())
                        .toList())
                .build();
    }
}
