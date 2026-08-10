package com.ratelimiter.controller;

import com.ratelimiter.dto.ClientConfigRequest;
import com.ratelimiter.entity.ClientConfig;
import com.ratelimiter.service.ClientConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ClientConfigService clientConfigService;

    /**
     * Requirement 02: per-client configurable limits (requests/sec, burst size)
     * set via an admin endpoint. "capacity" = burst size, "refillRate" = requests/sec,
     * "mode" picks TOKEN_BUCKET or SLIDING_WINDOW (requirement 05).
     */
    @PostMapping("/limits")
    public ResponseEntity<ClientConfig> setLimits(@Valid @RequestBody ClientConfigRequest request) {
        ClientConfig saved = clientConfigService.upsert(request);
        return ResponseEntity.status(HttpStatus.OK).body(saved);
    }
}
