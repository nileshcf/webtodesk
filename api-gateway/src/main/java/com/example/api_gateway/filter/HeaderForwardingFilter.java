package com.example.api_gateway.filter;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class HeaderForwardingFilter implements GlobalFilter, Ordered {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		return ReactiveSecurityContextHolder.getContext()
				.map(SecurityContext::getAuthentication)
				.flatMap(auth -> {
					if (auth != null && auth.getDetails() instanceof Claims claims) {

						String userId = (String) claims.get("userId");
						String email  = claims.getSubject();
						String roles  = claims.get("roles").toString();

						ServerHttpRequest mutatedRequest = exchange.getRequest()
								.mutate()
								.header("X-User-Id", userId)
								.header("X-User-Email", email)
								.header("X-User-Roles", roles)
								.build();

						return chain.filter(
								exchange.mutate().request(mutatedRequest).build()
						);
					}
					return chain.filter(exchange);
				})
				.switchIfEmpty(chain.filter(exchange));
	}

	@Override
	public int getOrder() {
		return -1;   // ✅ runs before routing
	}
}