package com.ratelimiter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Live, mutable token bucket state for one client.
 * Persisted (not in-memory only) so a service restart doesn't reset everyone's quota.
 */

@Entity
@Table(name = "token_bucket_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBucketState {

    @Id
    @Column(name = "client_key", length = 100)
    private String clientKey;

    /** Current tokens available. Kept as double so fractional refills aren't lost between checks. */
    @Column(nullable = false)
    private double tokens;

    /** Epoch millis of the last time we refilled this bucket. */
    @Column(name = "last_refill_timestamp", nullable = false)
    private long lastRefillTimestamp;
}
