package com.streamhub.platform.playback.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlaybackResponse {
    private String manifestUrl;
    private boolean locked;
    private String reason;
    private Long freeRemaining;
}
