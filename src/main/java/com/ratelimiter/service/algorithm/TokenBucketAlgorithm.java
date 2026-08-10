package com.ratelimiter.service.algorithm;

import com.ratelimiter.dto.RateLimitResult;
import com.ratelimiter.entity.ClientConfig;
import com.ratelimiter.entity.RateLimitMode;
import com.ratelimiter.entity.TokenBucketState;
import com.ratelimiter.repository.TokenBucketStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Classic lazy-refill token bucket:
 *   elapsed = now - lastRefillTimestamp
 *   tokens  = min(capacity, tokens + elapsed * refillRate)
 *   if tokens >= 1 -> ALLOW, tokens -= 1
 *   else           -> DENY
 *
 * No background job "ticks" the bucket - we only ever compute the refill
 * at the moment a request actually arrives. That's what makes this cheap
 * to run for millions of idle clients.
 */
@Component
@RequiredArgsConstructor
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final TokenBucketStateRepository repository;

    @Override
    public RateLimitMode getMode() {
        return RateLimitMode.TOKEN_BUCKET;
    }

    @Override
    public RateLimitResult check(ClientConfig config, long nowMillis) {
        // findByClientKeyForUpdate takes a DB row lock (SELECT ... FOR UPDATE).
        // Whichever concurrent request gets here first "wins" the row; the
        // other one physically blocks at the database until this transaction commits.
        TokenBucketState state = repository.findByClientKeyForUpdate(config.getClientKey())
                .orElseGet(() -> TokenBucketState.builder()
                        .clientKey(config.getClientKey())
                        .tokens(config.getCapacity())       // bucket starts full
                        .lastRefillTimestamp(nowMillis)
                        .build());

        long elapsedMillis = Math.max(0, nowMillis - state.getLastRefillTimestamp());
        double elapsedSeconds = elapsedMillis / 1000.0;
        double refilled = state.getTokens() + (elapsedSeconds * config.getRefillRate());
        double newTokenCount = Math.min(config.getCapacity(), refilled);

        boolean allowed = newTokenCount >= 1.0;
        if (allowed) {
            newTokenCount -= 1.0;
        }

        state.setTokens(newTokenCount);
        state.setLastRefillTimestamp(nowMillis);
        repository.save(state); // still inside the locked transaction - commit releases the lock

        // Seconds until at least 1 token will be available again (0 if we already have one).
        long resetSeconds;
        if (newTokenCount >= 1.0) {
            resetSeconds = 0;
        } else {
            double tokensNeeded = 1.0 - newTokenCount;
            double secondsNeeded = tokensNeeded / config.getRefillRate();
            resetSeconds = (long) Math.ceil(secondsNeeded);
        }

        return new RateLimitResult(
                allowed,
                config.getCapacity(),
                (int) Math.floor(newTokenCount),
                TimeUnit.MILLISECONDS.toSeconds(nowMillis) + resetSeconds
        );
    }
}
