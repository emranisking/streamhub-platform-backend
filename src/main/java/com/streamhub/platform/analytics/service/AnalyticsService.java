package com.streamhub.platform.analytics.service;

import com.streamhub.platform.analytics.dto.*;
import com.streamhub.platform.analytics.entity.TimeRange;
import com.streamhub.platform.analytics.entity.VisitLog;
import com.streamhub.platform.analytics.repository.VisitLogRepository;
import com.streamhub.platform.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers: "who visits every day / week / month / 6 months / year, how many
 * are unique, how many are already-registered users, how many are brand new
 * registrations, and how does each of those compare to the equivalent prior
 * period" - for every {@link TimeRange} filter.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final VisitLogRepository visitLogRepository;

    @Transactional
    public void recordVisit(Long userId, String sessionId, String ip) {
        VisitLog log = VisitLog.builder()
                .user(userId == null ? null : userRef(userId))
                .sessionId(sessionId)
                .ipAddress(ip)
                .visitedAt(LocalDateTime.now())
                .newRegistration(false)
                .build();
        visitLogRepository.save(log);
    }

    @Transactional
    public void recordRegistrationVisit(User user, HttpServletRequest request) {
        VisitLog log = VisitLog.builder()
                .user(user)
                .ipAddress(extractIp(request))
                .visitedAt(LocalDateTime.now())
                .newRegistration(true)
                .build();
        visitLogRepository.save(log);
    }

    public GrowthResponse getGrowth(TimeRange range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStart = range.currentStart(now);
        LocalDateTime previousStart = range.previousStart(now);
        LocalDateTime previousEnd = range.previousEnd(now);

        PeriodMetrics current = metricsFor(currentStart, now);
        PeriodMetrics previous = metricsFor(previousStart, previousEnd);

        List<BucketPoint> series = range.granularity() == TimeRange.Granularity.DAY
                ? mapBuckets(visitLogRepository.dailyBuckets(currentStart, now))
                : mapBuckets(visitLogRepository.monthlyBuckets(currentStart, now));

        return GrowthResponse.builder()
                .range(range)
                .current(current)
                .previous(previous)
                .visitsGrowthPercent(growthPercent(previous.getTotalVisits(), current.getTotalVisits()))
                .registrationsGrowthPercent(growthPercent(previous.getNewRegistrations(), current.getNewRegistrations()))
                .series(series)
                .build();
    }

    public DashboardResponse getDashboard() {
        Map<String, GrowthResponse> byRange = new LinkedHashMap<>();
        for (TimeRange range : TimeRange.values()) {
            byRange.put(range.name(), getGrowth(range));
        }
        return DashboardResponse.builder().byRange(byRange).build();
    }

    private PeriodMetrics metricsFor(LocalDateTime start, LocalDateTime end) {
        return PeriodMetrics.builder()
                .totalVisits(visitLogRepository.countVisits(start, end))
                .uniqueVisitors(visitLogRepository.countUniqueVisitors(start, end))
                .registeredUserVisits(visitLogRepository.countRegisteredUserVisits(start, end))
                .newRegistrations(visitLogRepository.countNewRegistrations(start, end))
                .build();
    }

    private Double growthPercent(long previous, long current) {
        if (previous == 0) {
            return current == 0 ? 0.0 : null; // null = "N/A" (undefined growth from a zero baseline)
        }
        return ((double) (current - previous) / previous) * 100.0;
    }

    @SuppressWarnings("unchecked")
    private List<BucketPoint> mapBuckets(List<Object[]> rows) {
        return rows.stream().map(row -> BucketPoint.builder()
                .periodStart(toLocalDateTime(row[0]))
                .totalVisits(((Number) row[1]).longValue())
                .uniqueVisitors(((Number) row[2]).longValue())
                .registeredUserVisits(((Number) row[3]).longValue())
                .newRegistrations(((Number) row[4]).longValue())
                .build()).toList();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        return null;
    }

    private String extractIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("x-forwarded-for");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private User userRef(Long userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
