package com.streamhub.platform.video.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LikeToggleResponse {
    private boolean liked;
    private long likes;
}
