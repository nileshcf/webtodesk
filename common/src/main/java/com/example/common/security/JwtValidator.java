package com.example.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtValidator {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;


    public JwtValidator(
            @Value("${jwt.access-secret}") String accessSecret,
            @Value("${jwt.refresh-secret}") String refreshSecret) {

        this.accessKey  = Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    // Call this on every API request
    public Claims validateAccessToken(String token) {
        return validate(token, accessKey, "access");
    }

    // Call this only on /refresh endpoint
    public Claims validateRefreshToken(String token) {
        return validate(token, refreshKey, "refresh");
    }

    private Claims validate(String token, SecretKey key, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Ensure token type matches what we expect
            String tokenType = (String) claims.get("type");
            if (!expectedType.equals(tokenType)) {
                throw new RuntimeException("Invalid token type. Expected: " + expectedType);
            }

            return claims;

        } catch (ExpiredJwtException e) {
            throw new RuntimeException("Token has expired", e);
        } catch (MalformedJwtException e) {
            throw new RuntimeException("Malformed token", e);
        } catch (SignatureException e) {
            throw new RuntimeException("Invalid token signature", e);
        } catch (UnsupportedJwtException e) {
            throw new RuntimeException("Unsupported token", e);
        }
    }
}