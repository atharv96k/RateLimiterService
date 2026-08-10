package com.ratelimiter.service;

import com.ratelimiter.dto.RateLimitResult;
import com.ratelimiter.entity.ClientConfig;
import com.ratelimiter.entity.RateLimitMode;
import com.ratelimiter.exception.ClientNotConfiguredException;
import com.ratelimiter.repository.ClientConfigRepository;
import com.ratelimiter.service.algorithm.RateLimitAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class RateLimitService {

    private final ClientConfigRepository clientConfigRepository;
    private final Map<RateLimitMode, RateLimitAlgorithm> algorithmsByMode;

    // Spring injects every RateLimitAlgorithm bean here (TokenBucketAlgorithm,
    // SlidingWindowAlgorithm, ...). We index them by mode once at startup so
    // picking the right one per request is just a map lookup - this is the
    // Strategy pattern, the same idea from the GoF patterns you studied.
    public RateLimitService(ClientConfigRepository clientConfigRepository,
                             List<RateLimitAlgorithm> algorithms) {
        this.clientConfigRepository = clientConfigRepository;
        this.algorithmsByMode = new EnumMap<>(RateLimitMode.class);
        for (RateLimitAlgorithm algorithm : algorithms) {
            this.algorithmsByMode.put(algorithm.getMode(), algorithm);
        }
    }

    /**
     * @Transactional here is what makes the pessimistic row lock inside the
     * algorithm actually hold: the lock is acquired and released within the
     * boundaries of THIS method call. If this weren't transactional, the
     * lock would be released the instant the repository call returns,
     * defeating the whole point.
     */
    @Transactional
    public RateLimitResult checkRequest(String clientKey) {
        ClientConfig config = clientConfigRepository.findById(clientKey)
                .orElseThrow(() -> new ClientNotConfiguredException(clientKey));

        RateLimitAlgorithm algorithm = algorithmsByMode.get(config.getMode());
        return algorithm.check(config, System.currentTimeMillis());
    }
}
