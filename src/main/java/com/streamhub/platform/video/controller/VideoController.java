package com.streamhub.platform.video.controller;

import com.streamhub.platform.common.pagination.PageResponse;
import com.streamhub.platform.common.pagination.PaginationRequest;
import com.streamhub.platform.common.pagination.PaginationService;
import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.playback.service.GuestTrackingService;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.service.UserService;
import com.streamhub.platform.video.dto.CheckLimitResponse;
import com.streamhub.platform.video.dto.VideoDetailResponse;
import com.streamhub.platform.video.dto.VideoSummaryResponse;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * Storyline: Alice discovers and browses here before ever logging in
 * (GET /videos, GET /videos/{id}). Bob and Carol like and view here too.
 * See /docs/API_STORYLINE.md section 1.
 */
@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
@Tag(name = "Videos")
public class VideoController {

    private final VideoService videoService;
    private final GuestTrackingService guestTrackingService;
    private final UserService userService;
    private final PaginationService paginationService;
    private final com.streamhub.platform.category.service.CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Paginated list of videos (public)", description = "Optional categoryId query param filters by category.")
    public ResponseEntity<ApiResponse<PageResponse<VideoSummaryResponse>>> list(
            @RequestParam(required = false) UUID categoryId,
            PaginationRequest pagination) {
        Page<Video> page = videoService.getVideos(Optional.ofNullable(categoryId), paginationService.resolve(pagination));
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.from(page, VideoSummaryResponse::from, paginationService.getCursorService())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full video details (public, Redis-cached)")
    public ResponseEntity<ApiResponse<VideoDetailResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(videoService.getDetailCached(id)));
    }

    @PatchMapping("/{id}/views")
    @Operation(summary = "Increment a video's view count",
            description = "Public - guests increment the raw counter; if a valid Authorization header is present, a watch-history entry is also recorded.")
    public ResponseEntity<ApiResponse<VideoDetailResponse>> incrementViews(@PathVariable UUID id) {
        User user = userService.getCurrentUserOptional().orElse(null);
        videoService.incrementViews(id, user);
        return ResponseEntity.ok(ApiResponse.ok(videoService.getDetailCached(id)));
    }

    @GetMapping("/{id}/check-limit")
    @Operation(summary = "Check remaining free plays without incrementing the counter")
    public ResponseEntity<ApiResponse<CheckLimitResponse>> checkLimit(
            @PathVariable UUID id,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "x-forwarded-for", required = false) String forwardedFor) {

        Optional<User> user = userService.getCurrentUserOptional();
        long count;
        boolean unlimited = false;

        if (user.isPresent() && user.get().hasActiveSubscription()) {
            unlimited = true;
            count = 0;
        } else if (user.isPresent()) {
            count = guestTrackingService.countForUser(user.get().getId());
        } else if (sessionId != null && !sessionId.isBlank()) {
            count = guestTrackingService.countForSession(sessionId);
        } else {
            count = guestTrackingService.countForIp(forwardedFor);
        }

        CheckLimitResponse response = CheckLimitResponse.builder()
                .unlimited(unlimited)
                .locked(!unlimited && guestTrackingService.isLocked(count))
                .remaining(unlimited ? -1 : guestTrackingService.freeRemaining(count))
                .build();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/increment-watch")
    @Operation(summary = "Increment the guest/user watch counter for a video without fetching the manifest",
            description = "Helper endpoint for frontend-side tracking, kept separate from /playback for UI use cases.")
    public ResponseEntity<ApiResponse<CheckLimitResponse>> incrementWatch(
            @PathVariable UUID id,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "x-forwarded-for", required = false) String forwardedFor) {

        Optional<User> user = userService.getCurrentUserOptional();
        long count;
        boolean unlimited = false;

        if (user.isPresent() && user.get().hasActiveSubscription()) {
            unlimited = true;
            count = 0;
        } else if (user.isPresent()) {
            count = guestTrackingService.incrementForUser(user.get().getId());
        } else if (sessionId != null && !sessionId.isBlank()) {
            count = guestTrackingService.incrementForSession(sessionId);
        } else {
            count = guestTrackingService.incrementForIp(forwardedFor);
        }

        CheckLimitResponse response = CheckLimitResponse.builder()
                .unlimited(unlimited)
                .locked(!unlimited && guestTrackingService.isLocked(count))
                .remaining(unlimited ? -1 : guestTrackingService.freeRemaining(count))
                .build();
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{id}/category")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a category to a video (ADMIN only)")
    public ResponseEntity<ApiResponse<VideoDetailResponse>> assignCategory(
            @PathVariable UUID id, @RequestParam UUID categoryId) {
        var category = categoryService.findById(categoryId);
        videoService.assignCategory(id, category);
        return ResponseEntity.ok(ApiResponse.ok(videoService.getDetailCached(id)));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a video (ADMIN only, soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        videoService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Video deleted"));
    }
}
