package com.example.common.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final JwtValidator jwtValidator;  // inject validator

    // Secrets injected from yml
    public JwtTokenProvider(
            @Value("${jwt.access-secret}") String accessSecret,
            @Value("${jwt.refresh-secret}") String refreshSecret, JwtValidator jwtValidator) {

        this.accessKey  = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtValidator = jwtValidator;
    }

    // ─────────────────────────────────────────
    // GENERATE
    // ─────────────────────────────────────────

    public String generateAccessToken(String username, Map<String, Object> claims, long expiresIn) {
        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresIn))
                .signWith(accessKey);

        if (claims != null && !claims.isEmpty()) {
            claims.forEach(builder::claim);
        }

        builder.claim("type", "access");

        return builder.compact();
    }

    public String generateRefreshToken(String username, long expiresIn) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiresIn))
                .claim("type", "refresh")
                .signWith(refreshKey)
                .compact();
    }

    // ─────────────────────────────────────────
    // EXTRACT — delegates validation to JwtValidator
    // ─────────────────────────────────────────

    public String extractUsernameFromAccessToken(String token) {
        return jwtValidator.validateAccessToken(token).getSubject();
    }

    public String extractUsernameFromRefreshToken(String token) {
        return jwtValidator.validateRefreshToken(token).getSubject();
    }

}