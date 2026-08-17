package com.streamhub.platform.video.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CheckLimitResponse {
    private boolean locked;
    /** -1 means unlimited (subscribed user). */
    private long remaining;
    private boolean unlimited;
}
