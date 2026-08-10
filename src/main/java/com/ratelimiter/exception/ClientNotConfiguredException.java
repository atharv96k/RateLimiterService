package com.ratelimiter.exception;

public class ClientNotConfiguredException extends RuntimeException {
    public ClientNotConfiguredException(String clientKey) {
        super("No rate-limit config found for clientKey='" + clientKey
                + "'. Register it via POST /api/v1/admin/limits first.");
    }
}
