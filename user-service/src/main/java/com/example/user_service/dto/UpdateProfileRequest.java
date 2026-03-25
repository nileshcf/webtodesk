package com.example.user_service.dto;

public record UpdateProfileRequest (
		String username,      // updatable
		String name,          // updatable
		Long phoneNumber,     // updatable
		String avatarUrl      // updatable
){ }
