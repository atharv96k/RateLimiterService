package com.ratelimiter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sliding-window-counter state for one client.
 * We use the "sliding window counter" approximation (previous window count,
 * weighted by overlap) instead of storing a timestamp log per request -
 * O(1) storage per client instead of O(requests).
 */
@Entity
@Table(name = "sliding_window_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlidingWindowState {

    @Id
    @Column(name = "client_key", length = 100)
    private String clientKey;

    /** Epoch millis marking the start of the CURRENT fixed window. */
    @Column(name = "current_window_start", nullable = false)
    private long currentWindowStart;

    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @Column(name = "previous_count", nullable = false)
    private int previousCount;
}
