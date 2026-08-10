package com.ratelimiter.dto;

/**
 * What an algorithm decides, before it's turned into HTTP headers/JSON.
 * Keeping this separate from the controller means TokenBucketAlgorithm and
 * SlidingWindowAlgorithm don't need to know anything about HTTP.
 */
public record RateLimitResult(
        boolean allowed,
        int limit,
        int remaining,
        long resetEpochSeconds
) {
}
