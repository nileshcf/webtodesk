package com.example.api_gateway.security;

import com.example.common.security.JwtValidator;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtValidator jwtValidator;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = authentication.getCredentials().toString();

        try {
            Claims claims = jwtValidator.validateAccessToken(token);

            String username = claims.getSubject();
            List<String> roles = claims.get("roles") != null
                    ? (List<String>) claims.get("roles")
                    : List.of("ROLE_USER");

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            auth.setDetails(claims);

            return Mono.just(auth);

        } catch (RuntimeException e) {
            // ✅ Log so you can see exactly what fails
            log.warn("Token validation failed: {}", e.getMessage());
            return Mono.empty();
        }
    }
}