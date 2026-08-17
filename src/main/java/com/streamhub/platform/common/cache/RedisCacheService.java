package com.streamhub.platform.common.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhub.platform.common.aop.ResponseSourceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thin, explicit Redis wrapper used by services that want fine-grained
 * control over cache keys (as opposed to the declarative {@code @Cacheable}
 * annotations used for simpler read-mostly data). Every read/write marks
 * {@link ResponseSourceContext} so the request-level AOP logger can report
 * whether a response came from Redis or the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.cache-ttl-seconds}")
    private long defaultTtlSeconds;

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            ResponseSourceContext.mark(ResponseSourceContext.Source.REDIS_CACHE);
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception e) {
            log.warn("Redis GET failed for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void set(String key, Object value) {
        set(key, value, Duration.ofSeconds(defaultTtlSeconds));
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Redis SET failed for key {}: {}", key, e.getMessage());
        }
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    /**
     * Read-through helper: returns the cached value if present, otherwise
     * calls {@code dbLoader}, marks the response source as DATABASE, and
     * populates the cache for next time.
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> dbLoader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            return cached.get();
        }
        T value = dbLoader.get();
        ResponseSourceContext.mark(ResponseSourceContext.Source.DATABASE);
        if (value != null) {
            set(key, value, ttl);
        }
        return value;
    }

    public long incrementWithTtl(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 0 : value;
    }

    public long getCounter(String key) {
        String raw = redisTemplate.opsForValue().get(key);
        return raw == null ? 0 : Long.parseLong(raw);
    }
}
