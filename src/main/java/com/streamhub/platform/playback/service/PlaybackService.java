package com.streamhub.platform.playback.service;

import com.streamhub.platform.playback.dto.PlaybackContext;
import com.streamhub.platform.playback.dto.PlaybackResponse;
import com.streamhub.platform.subscription.service.SubscriptionService;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.repository.UserRepository;
import com.streamhub.platform.video.entity.Video;
import com.streamhub.platform.video.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Core guest-watch-limiting logic. This is the single most important piece
 * of business logic carried over from the original platform - see
 * /docs/API_STORYLINE.md for the full walk-through of Alice/Bob/Carol.
 * <p>
 * Behavioural fixes vs. the original NestJS implementation:
 *  - one free-view limit, read only from {@link GuestTrackingService} (was
 *    hardcoded to two different values in two places).
 *  - the manifest is only fetched when the response is actually unlocked
 *    (the original fetched it unconditionally, even for locked responses).
 *  - guest IP tracking uses its own Redis key namespace instead of being
 *    silently aliased through the session-id key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaybackService {

    private final GuestTrackingService guestTrackingService;
    private final ManifestService manifestService;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final VideoService videoService;

    @Value("${app.media.public-route-prefix:/media}")
    private String publicRoutePrefix;

    @Value("${app.media.hls-output-directory:/home/emran/project/video_hls}")
    private String hlsOutputDirectory;

    public PlaybackResponse getPlayback(UUID videoId, PlaybackContext ctx) {

        if (ctx.getUserId() != null) {
            User user = userRepository.findById(ctx.getUserId()).orElse(null);
            if (user != null && user.hasActiveSubscription()) {
                // Subscribed users bypass guest tracking entirely.
                String manifest = getManifestUrl(videoId);
                log.info("🎬 Subscribed user playback - manifest: {}", manifest);
                return PlaybackResponse.builder()
                        .manifestUrl(manifest)
                        .locked(false)
                        .build();
            }
        }

        long count;
        boolean isAuthenticated = ctx.getUserId() != null;
        if (isAuthenticated) {
            count = guestTrackingService.incrementForUser(ctx.getUserId());
        } else if (ctx.getSessionId() != null && !ctx.getSessionId().isBlank()) {
            count = guestTrackingService.incrementForSession(ctx.getSessionId());
        } else {
            count = guestTrackingService.incrementForIp(ctx.getIp());
        }

        boolean locked = guestTrackingService.isLocked(count);

        if (locked) {
            String reason = isAuthenticated
                    ? "Payment required - subscribe to continue watching"
                    : "Account required - create an account to continue watching";
            return PlaybackResponse.builder()
                    .locked(true)
                    .reason(reason)
                    .freeRemaining(0L)
                    .build();
        }

        String manifest = getManifestUrl(videoId);
        log.info("🎬 Free user playback - manifest: {}", manifest);
        return PlaybackResponse.builder()
                .manifestUrl(manifest)
                .locked(false)
                .freeRemaining(guestTrackingService.freeRemaining(count))
                .build();
    }

    /**
     * ⭐ NEW: Get the manifest URL as a web-accessible path
     * Converts file system paths to web URLs
     */
    private String getManifestUrl(UUID videoId) {
        // Get the video entity
        Video video = videoService.getEntityById(videoId);
        String videoPath = video.getVideoUrl();

        log.info("🔄 Converting video path: {}", videoPath);

        if (videoPath == null || videoPath.isBlank()) {
            log.warn("⚠️ Video path is null or empty for video: {}", videoId);
            return null;
        }

        // If it's already a web path starting with /media/, return as-is
        if (videoPath.startsWith("/media/") || videoPath.startsWith(publicRoutePrefix)) {
            log.info("✅ Path already in web format: {}", videoPath);
            return videoPath;
        }

        // If it's already a full URL, return as-is
        if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
            log.info("✅ Path is already a full URL: {}", videoPath);
            return videoPath;
        }

        // Check if it's a file system path (contains /home/ or /video_hls/)
        if (videoPath.contains("/home/") || videoPath.contains("/video_hls/")) {
            // Try to extract the relative path from the HLS directory
            try {
                Path fullPath = Paths.get(videoPath);
                Path hlsDir = Paths.get(hlsOutputDirectory);

                // Try to relativize the path
                Path relativePath;
                try {
                    relativePath = hlsDir.relativize(fullPath);
                } catch (Exception e) {
                    // If relativize fails, try to extract the part after video_hls/
                    String pathStr = videoPath;
                    if (pathStr.contains("/video_hls/")) {
                        String afterHls = pathStr.substring(pathStr.indexOf("/video_hls/") + 11);
                        relativePath = Paths.get(afterHls);
                    } else {
                        // Just get the filename
                        String filename = pathStr.substring(pathStr.lastIndexOf("/") + 1);
                        relativePath = Paths.get(filename);
                    }
                }

                // Build the web path
                String webPath = publicRoutePrefix + "/" + relativePath.toString().replace("\\", "/");
                log.info("✅ Converted to web path: {}", webPath);
                return webPath;

            } catch (Exception e) {
                log.warn("⚠️ Failed to convert file path, falling back to filename only: {}", e.getMessage());
                // Fallback: extract just the filename
                String filename = videoPath.substring(videoPath.lastIndexOf("/") + 1);
                String fallbackPath = publicRoutePrefix + "/" + filename;
                log.info("⚠️ Fallback path: {}", fallbackPath);
                return fallbackPath;
            }
        }

        // If it's just a filename, assume it's in the HLS directory
        if (!videoPath.contains("/")) {
            String webPath = publicRoutePrefix + "/" + videoPath;
            log.info("✅ Using filename as web path: {}", webPath);
            return webPath;
        }

        // Last resort: just use the path as-is
        log.warn("⚠️ Using path as-is: {}", videoPath);
        return videoPath;
    }
}