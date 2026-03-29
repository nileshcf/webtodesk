package com.example.user_service.dto;

import java.util.List;

public record LoginResponse(
		String accessToken,
		String refreshToken,
		String tokenType,      // always "Bearer"
		long   expiresIn,      // seconds e.g. 3600
		String userId,
		String email,
		List<String> roles
) {}