package com.example.api_gateway.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

@Component
public class RouterValidator {

	// Must match pathMatchers in GatewaySecurityConfig
	public static final List<String> openApiEndpoints = List.of(
			"/user/auth/register",
			"/user/auth/login",
			"/user/auth/refresh",
			"/user/auth/google",
			"/user/auth/forgot-password",
			"/user/auth/reset-password",
			"/actuator/health",
			"/eureka"
	);

	// ✅ startsWith — precise matching
	public Predicate<ServerHttpRequest> isSecured =
			request -> openApiEndpoints
					.stream()
					.noneMatch(uri -> request.getURI().getPath().startsWith(uri));
}