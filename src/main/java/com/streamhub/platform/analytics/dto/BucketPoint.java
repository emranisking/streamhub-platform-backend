package com.streamhub.platform.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BucketPoint {
    private LocalDateTime periodStart;
    private long totalVisits;
    private long uniqueVisitors;
    private long registeredUserVisits;
    private long newRegistrations;
}
