package com.example.user_service.filter;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

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
		String requestId = UUID.randomUUID().toString().substring(0, 8);
		String userEmail = request.getHeader("X-User-Email");
		String query = request.getQueryString();
		String fullPath = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;

		MDC.put("requestId", requestId);

		// Log incoming request
		log.info(">>> [{}] {} {} - ip={} userEmail={}",
				requestId,
				request.getMethod(),
				fullPath,
				request.getRemoteAddr(),
				userEmail != null ? userEmail : "anonymous"
		);

		try {
			// Continue with request
			filterChain.doFilter(request, response);
		} catch (Exception e) {
			log.error("xxx [{}] {} {} failed: {}", requestId, request.getMethod(), fullPath, e.getMessage(), e);
			throw e;
		} finally {
			// Log response after completion
			long duration = System.currentTimeMillis() - startTime;
			log.info("<<< [{}] {} {} - status={} took={}ms",
					requestId,
					request.getMethod(),
					fullPath,
					response.getStatus(),
					duration
			);
			MDC.remove("requestId");
		}
	}
}
