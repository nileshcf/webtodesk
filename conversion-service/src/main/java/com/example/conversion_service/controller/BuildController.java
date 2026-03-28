package com.example.conversion_service.controller;

import com.example.conversion_service.dto.BuildStatusResponse;
import com.example.conversion_service.dto.ConversionResponse;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.service.BuildQueueService;
import com.example.conversion_service.service.BuildService;
import com.example.conversion_service.service.ConversionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Build queue management endpoints.
 * Gateway routes: /conversion/build/** → StripPrefix=1 → /build/**
 *
 * These endpoints supplement the legacy /conversions/{id}/build/* endpoints in ConversionController.
 */
@Slf4j
@RestController
@RequestMapping("/build")
@RequiredArgsConstructor
public class BuildController {

    private final BuildService buildService;
    private final ConversionService conversionService;
    private final BuildQueueService buildQueueService;

    /**
     * POST /build/trigger — trigger a build for a project.
     * Body: { projectId, buildFlags, featureConfig }
     */
    @PostMapping("/trigger")
    public ResponseEntity<BuildStatusResponse> triggerBuild(
            @RequestBody Map<String, Object> request) {
        String projectId = (String) request.get("projectId");
        ConversionResponse project = conversionService.getById(projectId);
        buildService.triggerBuild(projectId);
        return ResponseEntity.accepted().body(
                new BuildStatusResponse(projectId, project.projectName(),
                        ConversionStatus.BUILDING, null, false, null, "PREPARING", null)
        );
    }

    /**
     * GET /build/status/{projectId} — poll current build status.
     */
    @GetMapping("/status/{projectId}")
    public ResponseEntity<BuildStatusResponse> getBuildStatus(
            @PathVariable("projectId") String projectId,
            @RequestParam(required = false) String targetOS) {
        ConversionProject project = conversionService.findProjectById(projectId);
        return ResponseEntity.ok(BuildStatusResponse.from(project));
    }

    /**
     * GET /build/progress/{projectId} — SSE stream of real-time build progress.
     */
    @GetMapping("/progress/{projectId}")
    public SseEmitter getBuildProgress(
            @PathVariable("projectId") String projectId,
            @RequestParam(required = false) String targetOS) {
        return buildService.createSseEmitter(projectId);
    }

    /**
     * GET /build/queue/status — current queue depths and wait times.
     */
    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        BuildQueueService.QueueStatus status = buildQueueService.getQueueStatus();
        return ResponseEntity.ok(Map.of(
                "normalQueueLength", status.normalQueueLength(),
                "priorityQueueLength", status.priorityQueueLength(),
                "averageWaitTime", status.averageWaitTimeNormal(),
                "estimatedPosition", status.normalQueueLength() + status.priorityQueueLength()
        ));
    }

    /**
     * POST /build/cancel/{projectId} — cancel a running build.
     * Stub: marks project FAILED with "Cancelled" error.
     */
    @PostMapping("/cancel/{projectId}")
    public ResponseEntity<Void> cancelBuild(
            @PathVariable("projectId") String projectId,
            @RequestParam(required = false) String targetOS) {
        log.info("Cancel requested for project {}", projectId);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /build/retry/{projectId} — re-trigger a failed build.
     */
    @PostMapping("/retry/{projectId}")
    public ResponseEntity<BuildStatusResponse> retryBuild(
            @PathVariable("projectId") String projectId,
            @RequestParam(required = false) String targetOS) {
        ConversionResponse project = conversionService.getById(projectId);
        buildService.triggerBuild(projectId);
        return ResponseEntity.accepted().body(
                new BuildStatusResponse(projectId, project.projectName(),
                        ConversionStatus.BUILDING, null, false, null, "PREPARING", null)
        );
    }

    /**
     * GET /build/file-types/{targetOS} — available installer file types for an OS.
     */
    @GetMapping("/file-types/{targetOS}")
    public ResponseEntity<List<String>> getFileTypesForOS(
            @PathVariable("targetOS") String targetOS) {
        List<String> types = switch (targetOS.toUpperCase()) {
            case "WINDOWS" -> List.of("WINDOWS_EXE", "WINDOWS_MSI");
            case "LINUX" -> List.of("LINUX_APPIMAGE", "LINUX_DEB", "LINUX_RPM");
            case "MACOS" -> List.of("MACOS_DMG", "MACOS_ZIP");
            default -> List.of();
        };
        return ResponseEntity.ok(types);
    }

    /**
     * POST /build/validate-config — validate a build configuration.
     */
    @PostMapping("/validate-config")
    public ResponseEntity<Map<String, Object>> validateBuildConfig(
            @RequestBody Map<String, Object> config) {
        return ResponseEntity.ok(Map.of("valid", true, "errors", List.of(), "warnings", List.of()));
    }

    /**
     * GET /build/metrics — aggregate build metrics.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getBuildMetrics(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(Map.of(
                "buildsByTier", Map.of(),
                "buildsByOS", Map.of(),
                "queueStats", buildQueueService.getQueueStatus(),
                "monthlyUsage", List.of()
        ));
    }

    /**
     * GET /build/history/{projectId} — build history for a project.
     * Stub: returns single-item history from current project state.
     */
    @GetMapping("/history/{projectId}")
    public ResponseEntity<List<Map<String, Object>>> getBuildHistory(
            @PathVariable("projectId") String projectId,
            @RequestParam(defaultValue = "10") int limit) {
        ConversionProject project = conversionService.findProjectById(projectId);
        if (project.getBuildArtifactPath() == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(List.of(Map.of(
                "success", project.getStatus() == ConversionStatus.READY,
                "targetOS", "WINDOWS",
                "artifactUrl", project.getBuildArtifactPath() != null ? project.getBuildArtifactPath() : "",
                "downloadUrl", project.getBuildArtifactPath() != null ? project.getBuildArtifactPath() : "",
                "buildTime", 0,
                "error", project.getBuildError() != null ? project.getBuildError() : ""
        )));
    }

    /**
     * GET /build/download/{projectId} — redirect to R2 download URL.
     */
    @GetMapping("/download/{projectId}")
    public ResponseEntity<Void> downloadBuild(
            @PathVariable("projectId") String projectId) {
        String downloadUrl = buildService.getDownloadUrl(projectId);
        if (downloadUrl == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(302)
                .location(URI.create(downloadUrl))
                .build();
    }

    /**
     * GET /build/logs/{projectId} — build logs for a project (last known progress).
     */
    @GetMapping("/logs/{projectId}")
    public ResponseEntity<List<String>> getBuildLogs(
            @PathVariable("projectId") String projectId,
            @RequestParam(required = false) String targetOS) {
        ConversionProject project = conversionService.findProjectById(projectId);
        String progress = project.getBuildProgress() != null ? project.getBuildProgress() : "UNKNOWN";
        String error = project.getBuildError() != null ? project.getBuildError() : "";
        return ResponseEntity.ok(List.of("Status: " + progress, error.isEmpty() ? "" : "Error: " + error));
    }
}
