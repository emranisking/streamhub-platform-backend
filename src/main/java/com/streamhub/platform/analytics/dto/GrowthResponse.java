package com.streamhub.platform.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.streamhub.platform.analytics.entity.TimeRange;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrowthResponse {
    private TimeRange range;
    private PeriodMetrics current;
    private PeriodMetrics previous;
    /** Percentage change of totalVisits current vs. previous; null when previous == 0. */
    private Double visitsGrowthPercent;
    /** Percentage change of newRegistrations current vs. previous; null when previous == 0. */
    private Double registrationsGrowthPercent;
    private List<BucketPoint> series;
}
