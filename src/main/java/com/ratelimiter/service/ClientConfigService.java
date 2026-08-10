package com.ratelimiter.service;

import com.ratelimiter.dto.ClientConfigRequest;
import com.ratelimiter.entity.ClientConfig;
import com.ratelimiter.repository.ClientConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientConfigService {

    private final ClientConfigRepository clientConfigRepository;

    @Transactional
    public ClientConfig upsert(ClientConfigRequest request) {
        ClientConfig config = ClientConfig.builder()
                .clientKey(request.clientKey())
                .capacity(request.capacity())
                .refillRate(request.refillRate())
                .mode(request.mode())
                .windowSizeMs(request.windowSizeMs() != null ? request.windowSizeMs() : 1000L)
                .build();
        return clientConfigRepository.save(config);
    }
}
