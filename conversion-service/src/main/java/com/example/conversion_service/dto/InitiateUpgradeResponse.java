package com.example.conversion_service.dto;

public record InitiateUpgradeResponse(
        String upgradeUrl,
        String sessionId
) {}
