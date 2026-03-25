package com.example.user_service.filter;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// filter/RequestLoggingFilter.java
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                                HttpServletResponse response,
	                                FilterChain filterChain)
			throws ServletException, IOException {

		long startTime = System.currentTimeMillis();

		// Log incoming request
		log.info(">>> {} {} - from IP: {}",
				request.getMethod(),
				request.getRequestURI(),
				request.getRemoteAddr()
		);

		// Continue with request
		filterChain.doFilter(request, response);

		// Log response after completion
		long duration = System.currentTimeMillis() - startTime;
		log.info("<<< {} {} - status: {} - took: {}ms",
				request.getMethod(),
				request.getRequestURI(),
				response.getStatus(),
				duration
		);
	}
}
