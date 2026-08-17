package com.streamhub.platform.playback.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PlaybackContext {
    private Long userId;
    private String sessionId;
    private String ip;
}
