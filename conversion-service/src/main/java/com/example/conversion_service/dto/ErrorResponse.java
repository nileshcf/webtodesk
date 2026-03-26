package com.example.conversion_service.dto;

import java.time.Instant;

public record ErrorResponse(
        String error,
        String message,
        int status,
        Instant timestamp
) {
    public ErrorResponse(String error, String message, int status) {
        this(error, message, status, Instant.now());
    }
}

