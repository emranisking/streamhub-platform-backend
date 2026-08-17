package com.streamhub.platform.like.dto;

import com.streamhub.platform.like.entity.UserLike;
import com.streamhub.platform.video.dto.VideoSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class UserLikeResponse {
    private UUID id;
    private VideoSummaryResponse video;
    private LocalDateTime likedAt;

    public static UserLikeResponse from(UserLike like) {
        return UserLikeResponse.builder()
                .id(like.getId())
                .video(VideoSummaryResponse.from(like.getVideo()))
                .likedAt(like.getLikedAt())
                .build();
    }
}
