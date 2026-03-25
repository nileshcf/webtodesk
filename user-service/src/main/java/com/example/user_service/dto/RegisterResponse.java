package com.example.user_service.dto;

import java.time.LocalDateTime;

public record RegisterResponse(
		String message,
		String email,
		String userId,
		LocalDateTime createdAt) {
}
