package com.streamhub.platform.playback.controller;

import com.streamhub.platform.common.response.ApiResponse;
import com.streamhub.platform.playback.dto.PlaybackContext;
import com.streamhub.platform.playback.dto.PlaybackResponse;
import com.streamhub.platform.playback.service.PlaybackService;
import com.streamhub.platform.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Storyline: this is where Alice (guest), Bob (free user) and Carol
 * (subscriber) all diverge. See /docs/API_STORYLINE.md section 2.
 */
@RestController
@RequestMapping("/api/v1/playback")
@RequiredArgsConstructor
@Tag(name = "Playback")
public class PlaybackController {

    private final PlaybackService playbackService;
    private final UserService userService;

    @GetMapping("/{videoId}")
    @Operation(summary = "Get a playback manifest, or a locked response if the free-view limit was reached",
            description = "Authorization header is optional. Guests may pass x-session-id to be tracked by session instead of IP.")
    public ResponseEntity<ApiResponse<PlaybackResponse>> play(
            @PathVariable UUID videoId,
            @RequestHeader(value = "x-session-id", required = false) String sessionId,
            HttpServletRequest request) {

        Long userId = userService.getCurrentUserOptional().map(u -> u.getId()).orElse(null);
        String ip = extractIp(request);

        PlaybackContext ctx = PlaybackContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .ip(ip)
                .build();

        PlaybackResponse response = playbackService.getPlayback(videoId, ctx);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private String extractIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("x-forwarded-for");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
