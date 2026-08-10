package com.ratelimiter.service.algorithm;

import com.ratelimiter.dto.RateLimitResult;
import com.ratelimiter.entity.ClientConfig;
import com.ratelimiter.entity.RateLimitMode;
import com.ratelimiter.entity.SlidingWindowState;
import com.ratelimiter.repository.SlidingWindowStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * "Sliding window counter" - a practical approximation of a true sliding
 * log that doesn't require storing every request timestamp.
 *
 * We split time into fixed windows (size = config.windowSizeMs) and keep a
 * count for the CURRENT window plus the PREVIOUS one. The estimated request
 * count "right now" is a weighted blend of the two, weighted by how far we
 * are into the current window:
 *
 *   estimate = previousCount * (fraction of previous window still "in view") + currentCount
 *
 * This smooths out the hard edge you'd get from a plain fixed-window
 * counter (where a client could burst 2x the limit right at a window
 * boundary).
 */
@Component
@RequiredArgsConstructor
public class SlidingWindowAlgorithm implements RateLimitAlgorithm {

    private final SlidingWindowStateRepository repository;

    @Override
    public RateLimitMode getMode() {
        return RateLimitMode.SLIDING_WINDOW;
    }

    @Override
    public RateLimitResult check(ClientConfig config, long nowMillis) {
        long windowSizeMs = config.getWindowSizeMs();
        long windowStart = (nowMillis / windowSizeMs) * windowSizeMs;

        SlidingWindowState state = repository.findByClientKeyForUpdate(config.getClientKey())
                .orElseGet(() -> SlidingWindowState.builder()
                        .clientKey(config.getClientKey())
                        .currentWindowStart(windowStart)
                        .currentCount(0)
                        .previousCount(0)
                        .build());

        long windowsElapsed = (windowStart - state.getCurrentWindowStart()) / windowSizeMs;

        if (windowsElapsed == 1) {
            // Rolled into the very next window - last window becomes "previous".
            state.setPreviousCount(state.getCurrentCount());
            state.setCurrentCount(0);
            state.setCurrentWindowStart(windowStart);
        } else if (windowsElapsed > 1) {
            // Client was idle for more than one full window - nothing carries over.
            state.setPreviousCount(0);
            state.setCurrentCount(0);
            state.setCurrentWindowStart(windowStart);
        }
        // windowsElapsed == 0 -> still in the same window, no shift needed.

        double elapsedInCurrentWindow = nowMillis - state.getCurrentWindowStart();
        double weightOfPreviousWindow = 1.0 - (elapsedInCurrentWindow / (double) windowSizeMs);
        weightOfPreviousWindow = Math.max(0.0, Math.min(1.0, weightOfPreviousWindow));

        double estimatedCount = (state.getPreviousCount() * weightOfPreviousWindow) + state.getCurrentCount();

        boolean allowed = estimatedCount < config.getCapacity();
        if (allowed) {
            state.setCurrentCount(state.getCurrentCount() + 1);
        }
        repository.save(state);

        int remaining = (int) Math.max(0, config.getCapacity() - Math.ceil(estimatedCount) - (allowed ? 1 : 0));
        long resetEpochSeconds = TimeUnit.MILLISECONDS.toSeconds(state.getCurrentWindowStart() + windowSizeMs);

        return new RateLimitResult(allowed, config.getCapacity(), remaining, resetEpochSeconds);
    }
}
