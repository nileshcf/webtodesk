package com.example.conversion_service.filter;

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

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        String requestId = uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
        
        String userEmail = sanitizeUserEmail(request.getHeader("X-User-Email"));
        String clientIp = getClientIp(request);
        String fullPath = buildFullPath(request);

        try (MDC.MDCCloseable closeable = MDC.putCloseable("requestId", requestId)) {
            log.info(">>> [{}] {} {} - ip={} userEmail={}",
                    requestId,
                    request.getMethod(),
                    fullPath,
                    clientIp,
                    userEmail
            );

            try {
                filterChain.doFilter(request, response);
            } catch (Exception e) {
                log.error("ERROR [{}] {} {} failed: {}", requestId, request.getMethod(), fullPath, e.getMessage(), e);
                throw e;
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                log.info("<<< [{}] {} {} - status={} took={}ms",
                        requestId,
                        request.getMethod(),
                        fullPath,
                        response.getStatus(),
                        duration
                );
            }
        }
    }

    /**
     * Get the real client IP address, considering proxy headers
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            // X-Forwarded-For can contain multiple IPs, take the first one (original client)
            return xff.split(",")[0].trim();
        }
        
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Sanitize user email to prevent log injection
     */
    private String sanitizeUserEmail(String email) {
        if (email == null) {
            return "anonymous";
        }
        // Remove newlines, tabs, and other control characters that could be used for log injection
        return email.replaceAll("[\r\n\t\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u000b\u000c\u000e\u000f\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f]", "_");
    }

    /**
     * Build the full path including query string efficiently
     */
    private String buildFullPath(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return request.getRequestURI();
        }
        
        StringBuilder sb = new StringBuilder(request.getRequestURI().length() + query.length() + 1);
        sb.append(request.getRequestURI()).append('?').append(query);
        return sb.toString();
    }
}

