package com.example.common.dto;

import java.time.Instant;

public record ErrorResponse(
		String error,
		String message,
		int status,
		Instant timestamp
) {
	// ✅ Convenience constructor — auto sets timestamp
	public ErrorResponse(String error, String message, int status) {
		this(error, message, status, Instant.now());
	}
}