package com.example.common.security;


import org.springframework.beans.factory.annotation.Value;

public class JwtConstants {
    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";
    // In production, load this from environment variables/vault

    public static final long ACCESS_TOKEN_EXPIRY  = 15 * 60 * 1000L;        // 15 minutes
    public static final long REFRESH_TOKEN_EXPIRY = 30L * 24 * 60 * 60 * 1000L;
}