package com.streamhub.platform.common.aop;

/**
 * Thread-local flag services use to record where the data returned by the
 * current request actually came from (Redis cache or the database), so the
 * {@link LoggingAspect} can print it alongside method, path, and timing for
 * every endpoint call, as required for production observability.
 */
public final class ResponseSourceContext {

    public enum Source { REDIS_CACHE, DATABASE, MIXED, UNKNOWN }

    private static final ThreadLocal<Source> HOLDER = ThreadLocal.withInitial(() -> Source.UNKNOWN);

    private ResponseSourceContext() {
    }

    public static void mark(Source source) {
        Source current = HOLDER.get();
        if (current == Source.UNKNOWN || current == source) {
            HOLDER.set(source);
        } else {
            HOLDER.set(Source.MIXED);
        }
    }

    public static Source get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
