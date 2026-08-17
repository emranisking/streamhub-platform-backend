package com.streamhub.platform.analytics.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class DashboardResponse {
    /** Keyed by TimeRange name: DAILY, WEEKLY, MONTHLY, SIX_MONTHS, YEARLY. */
    private Map<String, GrowthResponse> byRange;
}
