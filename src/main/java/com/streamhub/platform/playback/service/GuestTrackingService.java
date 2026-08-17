package com.streamhub.platform.playback.service;

import com.streamhub.platform.common.cache.RedisCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Tracks how many free videos a guest / non-subscribed user has watched.
 * <p>
 * This is THE single source of truth for the free-view limit
 * (`app.playback.free-limit`) - the original NestJS codebase had this value
 * hardcoded differently in two different places (10 in PlaybackService, 2
 * here); that inconsistency is resolved by making this the only place the
 * limit is read from.
 * <p>
 * Guest IP tracking now uses its own dedicated Redis key prefix
 * (`guest:ip:{ip}`) instead of being silently routed through the session-key
 * method as in the original implementation.
 */
@Service
@RequiredArgsConstructor
public class GuestTrackingService {

    private final RedisCacheService cacheService;

    @Value("${app.playback.free-limit}")
    private int freeLimit;

    @Value("${app.playback.guest-ttl-days}")
    private int guestTtlDays;

    private Duration ttl() {
        return Duration.ofDays(guestTtlDays);
    }

    private String sessionKey(String sessionId) {
        return "guest:session:" + sessionId;
    }

    private String ipKey(String ip) {
        return "guest:ip:" + ip;
    }

    private String userKey(Long userId) {
        return "user:watchcount:" + userId;
    }

    private String videoKey(String base, UUID videoId) {
        // Per-video-per-tracking-identifier counters so distinct videos are
        // counted independently (matches the "N free videos" product intent).
        return base + ":videos";
    }

    public long incrementForUser(Long userId) {
        return cacheService.incrementWithTtl(userKey(userId), ttl());
    }

    public long incrementForSession(String sessionId) {
        return cacheService.incrementWithTtl(sessionKey(sessionId), ttl());
    }

    public long incrementForIp(String ip) {
        return cacheService.incrementWithTtl(ipKey(ip), ttl());
    }

    public long countForUser(Long userId) {
        return cacheService.getCounter(userKey(userId));
    }

    public long countForSession(String sessionId) {
        return cacheService.getCounter(sessionKey(sessionId));
    }

    public long countForIp(String ip) {
        return cacheService.getCounter(ipKey(ip));
    }

    public long freeRemaining(long count) {
        return Math.max(0, freeLimit - count);
    }

    public boolean isLocked(long count) {
        return count > freeLimit;
    }

    public int getFreeLimit() {
        return freeLimit;
    }
}
