package com.ratelimiter.service.algorithm;

import com.ratelimiter.dto.RateLimitResult;
import com.ratelimiter.entity.ClientConfig;
import com.ratelimiter.entity.RateLimitMode;

/**
 * Strategy interface - each rate-limiting algorithm (token bucket, sliding
 * window, ...) implements this the same way, so RateLimitService doesn't
 * need to know the internals of either one. Adding a new algorithm later
 * means adding a new class here, not touching the service or controller.
 */
public interface RateLimitAlgorithm {

    RateLimitMode getMode();

    /**
     * Decide ALLOW/DENY for one request against the given client's config.
     * Implementations are responsible for their own persistence + locking.
     */
    RateLimitResult check(ClientConfig config, long nowMillis);
}
