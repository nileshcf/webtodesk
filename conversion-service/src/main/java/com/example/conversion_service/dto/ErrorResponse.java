package com.example.conversion_service.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        String message,
        Map<String, String> errors,
        Instant timestamp,
        String path
) {
    public static ErrorResponse of(String message, String path) {
        return new ErrorResponse(message, Map.of(), Instant.now(), path);
    }

    public static ErrorResponse of(String message, Map<String, String> errors, String path) {
        return new ErrorResponse(message, errors, Instant.now(), path);
    }
}
