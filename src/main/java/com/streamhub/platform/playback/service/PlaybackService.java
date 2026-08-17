package com.streamhub.platform.playback.service;

import com.streamhub.platform.playback.dto.PlaybackContext;
import com.streamhub.platform.playback.dto.PlaybackResponse;
import com.streamhub.platform.subscription.service.SubscriptionService;
import com.streamhub.platform.user.entity.User;
import com.streamhub.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
@Service
@RequiredArgsConstructor
public class PlaybackService {

    private final GuestTrackingService guestTrackingService;
    private final ManifestService manifestService;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public PlaybackResponse getPlayback(UUID videoId, PlaybackContext ctx) {

        if (ctx.getUserId() != null) {
            User user = userRepository.findById(ctx.getUserId()).orElse(null);
            if (user != null && user.hasActiveSubscription()) {
                // Subscribed users bypass guest tracking entirely.
                String manifest = manifestService.getManifestUrl(videoId);
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

        String manifest = manifestService.getManifestUrl(videoId);
        return PlaybackResponse.builder()
                .manifestUrl(manifest)
                .locked(false)
                .freeRemaining(guestTrackingService.freeRemaining(count))
                .build();
    }
}
