package com.streamhub.platform.analytics.controller;

import com.streamhub.platform.analytics.dto.DashboardResponse;
import com.streamhub.platform.analytics.dto.GrowthResponse;
import com.streamhub.platform.analytics.dto.TrackVisitRequest;
import com.streamhub.platform.analytics.entity.TimeRange;
import com.streamhub.platform.analytics.service.AnalyticsService;
import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Storyline: this is the ADMIN / ANALYTIC role's dashboard - "who is
 * visiting the platform, how many are new, and is that growing or
 * shrinking" for every filter window (day/week/month/6 months/year). See
 * /docs/API_STORYLINE.md section 5.
 * <p>
 * `/track` is the one public endpoint here: the frontend calls it once per
 * app load / session so visits are recorded without turning every single
 * API call into a counted "visit".
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService userService;

    @PostMapping("/track")
    @Operation(summary = "Record a visit (public)", description = "Call once per app load / session from the frontend.")
    public ResponseEntity<ApiResponse<Void>> track(@RequestBody(required = false) TrackVisitRequest request,
                                                     HttpServletRequest httpRequest) {
        Long userId = userService.getCurrentUserOptional().map(u -> u.getId()).orElse(null);
        String sessionId = request == null ? null : request.getSessionId();
        analyticsService.recordVisit(userId, sessionId, extractIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.message("Visit recorded"));
    }

    @GetMapping("/visits")
    @Operation(summary = "Visit + growth metrics for a given range (ADMIN/ANALYTIC only)",
            description = "range = DAILY | WEEKLY | MONTHLY | SIX_MONTHS | YEARLY")
    public ResponseEntity<ApiResponse<GrowthResponse>> visits(@RequestParam(defaultValue = "DAILY") TimeRange range) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getGrowth(range)));
    }

    @GetMapping("/registrations")
    @Operation(summary = "Registration + growth metrics for a given range (ADMIN/ANALYTIC only)",
            description = "Same payload shape as /visits; use newRegistrations / registrationsGrowthPercent.")
    public ResponseEntity<ApiResponse<GrowthResponse>> registrations(@RequestParam(defaultValue = "DAILY") TimeRange range) {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getGrowth(range)));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "All five ranges at once (ADMIN/ANALYTIC only)")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getDashboard()));
    }

    private String extractIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("x-forwarded-for");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
