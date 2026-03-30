package com.example.conversion_service.controller;

import com.example.conversion_service.repository.ConversionRepository;
import com.example.conversion_service.service.BuildQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Health", description = "Service health, uptime, DB connectivity, and build queue status")
@RestController
@RequestMapping("/conversions")
@RequiredArgsConstructor
public class HealthController {

    private static final Instant START_TIME = Instant.now();

    private final ConversionRepository conversionRepository;
    private final BuildQueueService buildQueueService;

    @Value("${spring.application.name:conversion-service}")
    private String serviceName;

    @Operation(summary = "Detailed health check — DB ping, uptime, build queue depth")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> db = checkDatabase();
        Map<String, Object> queue = checkBuildQueue();

        boolean allUp = "UP".equals(db.get("status"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allUp ? "UP" : "DEGRADED");
        body.put("service", serviceName);
        body.put("version", "1.8.0");
        body.put("uptime", formatUptime());
        body.put("startTime", START_TIME.toString());
        body.put("components", Map.of(
                "database", db,
                "buildQueue", queue
        ));

        return ResponseEntity.ok(body);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private Map<String, Object> checkDatabase() {
        try {
            long count = conversionRepository.count();
            return Map.of("status", "UP", "totalProjects", count);
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage());
        }
    }

    private Map<String, Object> checkBuildQueue() {
        try {
            var qs = buildQueueService.getQueueStatus();
            return Map.of(
                    "status", "UP",
                    "normalQueueDepth", qs.normalQueueLength(),
                    "priorityQueueDepth", qs.priorityQueueLength(),
                    "avgWaitNormalMin", qs.averageWaitTimeNormal(),
                    "avgWaitPriorityMin", qs.averageWaitTimePriority()
            );
        } catch (Exception e) {
            return Map.of("status", "UNKNOWN", "error", e.getMessage());
        }
    }

    private String formatUptime() {
        Duration uptime = Duration.between(START_TIME, Instant.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
}
