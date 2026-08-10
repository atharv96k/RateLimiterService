package com.ratelimiter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-configurable limit settings for one client.
 * This is intentionally kept separate from the live bucket/window STATE
 * (see TokenBucketState / SlidingWindowState) so that changing a client's
 * limit never wipes out their current usage counters by accident.
 */
@Entity
@Table(name = "client_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientConfig {

    @Id
    @Column(name = "client_key", length = 100)
    private String clientKey;

    /** Max tokens in the bucket / max requests allowed within one window. */
    @Column(nullable = false)
    private int capacity;

    /** Tokens refilled per second (token bucket mode only). */
    @Column(name = "refill_rate", nullable = false)
    private double refillRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RateLimitMode mode = RateLimitMode.TOKEN_BUCKET;

    /** Window size in milliseconds, sliding-window mode only. Default 1000ms = 1s. */
    @Column(name = "window_size_ms", nullable = false)
    @Builder.Default
    private long windowSizeMs = 1000L;
}
