package com.streamhub.platform.analytics.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrackVisitRequest {
    /** Client-generated session id; used for guests when no auth token is present. */
    private String sessionId;
}
