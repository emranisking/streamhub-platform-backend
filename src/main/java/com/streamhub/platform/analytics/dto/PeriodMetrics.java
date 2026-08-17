package com.streamhub.platform.analytics.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PeriodMetrics {
    private long totalVisits;
    private long uniqueVisitors;
    private long registeredUserVisits;
    private long newRegistrations;
}
