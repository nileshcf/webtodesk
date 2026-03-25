package com.example.api_gateway.security;

import com.example.common.security.JwtConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SecurityContextRepository implements ServerSecurityContextRepository {

    private final AuthenticationManager authenticationManager;

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        // ✅ Stateless — we never store security context on server side
        throw new UnsupportedOperationException("Stateless JWT implementation does not support save");
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        return Mono.justOrEmpty(
                        exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)
                )
                // ✅ Only process if it starts with "Bearer "
                .filter(authHeader -> authHeader.startsWith(JwtConstants.PREFIX))

                // ✅ Strip "Bearer " prefix to get raw token
                .map(authHeader -> authHeader.substring(JwtConstants.PREFIX.length()))

                // ✅ Pass token to AuthenticationManager for validation
                .flatMap(token -> {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(token, token);

                    return authenticationManager.authenticate(auth)
                            .map(SecurityContextImpl::new)
                            .onErrorResume(e -> Mono.empty()); // ✅ invalid token → empty context
                });
    }
}