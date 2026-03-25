package com.example.user_service.dto;

public record RefreshResponse(String accessToken,
                              String tokenType,
                              Long expiresIn)
{ }
