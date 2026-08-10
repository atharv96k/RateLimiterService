package com.ratelimiter.controller;

import com.ratelimiter.dto.RateLimitResult;
import com.ratelimiter.service.RateLimitService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rate-limit")
@RequiredArgsConstructor
@Validated
public class RateLimitController {

    private final RateLimitService rateLimitService;

    /**
     * Requirement 01: given a client key, returns ALLOW or DENY.
     * Requirement 06: standard rate-limit headers on every response, both
     * ALLOW and DENY - callers should be able to read their quota status
     * without parsing the body.
     */
    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> check(@RequestParam("clientKey") @NotBlank String clientKey) {
        RateLimitResult result = rateLimitService.checkRequest(clientKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientKey", clientKey);
        body.put("decision", result.allowed() ? "ALLOW" : "DENY");

        HttpStatus status = result.allowed() ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;

        return ResponseEntity.status(status)
                .header("X-RateLimit-Limit", String.valueOf(result.limit()))
                .header("X-RateLimit-Remaining", String.valueOf(result.remaining()))
                .header("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()))
                .body(body);
    }
}
