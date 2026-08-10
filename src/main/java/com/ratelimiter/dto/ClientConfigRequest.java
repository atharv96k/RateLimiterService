package com.ratelimiter.dto;

import com.ratelimiter.entity.RateLimitMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ClientConfigRequest(

        @NotBlank(message = "clientKey is required")
        String clientKey,

        @Min(value = 1, message = "capacity must be at least 1")
        int capacity,

        @Positive(message = "refillRate must be > 0")
        double refillRate,

        @NotNull(message = "mode is required")
        RateLimitMode mode,

        Long windowSizeMs // optional, defaults to 1000ms if null - only used for SLIDING_WINDOW
) {
}
