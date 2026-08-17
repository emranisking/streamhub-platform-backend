package com.streamhub.platform.analytics.entity;

import java.time.LocalDateTime;

/**
 * Every analytics endpoint accepts one of these as the `range` filter.
 * Each range defines a lookback window (relative to now) and, for
 * historical series, a bucket granularity used to group results.
 */
public enum TimeRange {
    DAILY(1, Granularity.DAY),
    WEEKLY(7, Granularity.DAY),
    MONTHLY(30, Granularity.DAY),
    SIX_MONTHS(182, Granularity.MONTH),
    YEARLY(365, Granularity.MONTH);

    public enum Granularity { DAY, MONTH }

    private final int windowDays;
    private final Granularity granularity;

    TimeRange(int windowDays, Granularity granularity) {
        this.windowDays = windowDays;
        this.granularity = granularity;
    }

    public Granularity granularity() {
        return granularity;
    }

    public LocalDateTime currentStart(LocalDateTime now) {
        return now.minusDays(windowDays);
    }

    public LocalDateTime previousStart(LocalDateTime now) {
        return now.minusDays((long) windowDays * 2);
    }

    public LocalDateTime previousEnd(LocalDateTime now) {
        return currentStart(now);
    }
}
