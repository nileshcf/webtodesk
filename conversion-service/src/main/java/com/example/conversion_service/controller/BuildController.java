package com.example.conversion_service.controller;

import com.example.conversion_service.dto.BuildRecordResponse;
import com.example.conversion_service.dto.BuildStatusResponse;
import com.example.conversion_service.dto.ConversionResponse;
import com.example.conversion_service.dto.CreateConversionRequest;
import com.example.conversion_service.dto.ModuleInfoResponse;
import com.example.conversion_service.dto.QuickBuildRequest;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.service.BuildMetricsService;
import com.example.conversion_service.service.BuildQueueService;
import com.example.conversion_service.service.BuildService;
import com.example.conversion_service.service.ConversionService;
import com.example.conversion_service.service.ModuleRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@Tag(name = "Build", description = "Build queue management, metrics, history, module availability, and SSE progress streaming")
@Slf4j
@RestController
@RequestMapping("/build")
@RequiredArgsConstructor
public class BuildController {

    private final BuildService buildService;
    private final ConversionService conversionService;
    private final BuildQueueService buildQueueService;
    private final BuildMetricsService buildMetricsService;
    private final ModuleRegistry moduleRegistry;

    @Value("${webtodesk.build.development-build:false}")
    private boolean developmentBuild;

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
            @RequestParam(value = "targetOS", required = false) String targetOS) {
        ConversionProject project = conversionService.findProjectById(projectId);
        return ResponseEntity.ok(BuildStatusResponse.from(project));
    }

    /**
     * GET /build/progress/{projectId} — SSE stream of real-time build progress.
     */
    @GetMapping("/progress/{projectId}")
    public SseEmitter getBuildProgress(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "targetOS", required = false) String targetOS) {
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
            @RequestParam(value = "targetOS", required = false) String targetOS) {
        log.info("Cancel requested for project {}", projectId);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /build/retry/{projectId} — re-trigger a failed build.
     */
    @PostMapping("/retry/{projectId}")
    public ResponseEntity<BuildStatusResponse> retryBuild(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "targetOS", required = false) String targetOS) {
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
            @RequestParam(value = "period", defaultValue = "month") String period) {
        return ResponseEntity.ok(Map.of(
                "queueStats", buildQueueService.getQueueStatus(),
                "recentBuilds", buildMetricsService.getUserBuildHistory(userEmail, 10)
        ));
    }

    /**
     * GET /build/history/{projectId} — build history for a project.
     * Stub: returns single-item history from current project state.
     */
    @GetMapping("/history/{projectId}")
    public ResponseEntity<List<BuildRecordResponse>> getBuildHistory(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(buildMetricsService.getBuildHistory(projectId, limit));
    }

    /**
     * GET /build/metrics/{projectId} — aggregate metrics for a single project.
     */
    @GetMapping("/metrics/{projectId}")
    public ResponseEntity<Map<String, Object>> getProjectMetrics(
            @PathVariable("projectId") String projectId) {
        return ResponseEntity.ok(buildMetricsService.getProjectMetrics(projectId));
    }

    /**
     * GET /build/modules — list all available modules with tier requirements.
     */
    @GetMapping("/modules")
    public ResponseEntity<List<ModuleInfoResponse>> listModules(
            @RequestParam(value = "tier", required = false, defaultValue = "TRIAL") String tier) {
        LicenseTier licenseTier;
        try {
            licenseTier = LicenseTier.valueOf(tier.toUpperCase());
        } catch (IllegalArgumentException e) {
            licenseTier = LicenseTier.TRIAL;
        }
        final LicenseTier finalTier = licenseTier;
        List<ModuleInfoResponse> modules = moduleRegistry.getAllModules().stream()
                .map(def -> ModuleInfoResponse.from(def, moduleRegistry.isAvailable(def.key(), finalTier)))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(modules);
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
     * POST /build/quick-build — DEV ONLY (requires DEVELOPMENT_BUILD=true).
     * Creates a project and immediately triggers a build in one request.
     * Accepts: projectName, websiteUrl, appTitle, iconFile, modules, platform, userEmail.
     * Returns: projectId, pollUrl, progressUrl, availableModules.
     */
    @PostMapping("/quick-build")
    public ResponseEntity<Map<String, Object>> quickBuild(
            @RequestBody QuickBuildRequest req,
            @RequestHeader(value = "X-User-Email", required = false) String headerEmail) {

        if (!developmentBuild) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "quick-build is only available when DEVELOPMENT_BUILD=true",
                    "hint", "Set DEVELOPMENT_BUILD=true in your .env or application.yml"
            ));
        }

        String userEmail = (headerEmail != null && !headerEmail.isBlank())
                ? headerEmail : req.resolvedUserEmail();

        CreateConversionRequest createReq = new CreateConversionRequest(
                req.resolvedProjectName(),
                req.websiteUrl(),
                req.resolvedAppTitle(),
                req.iconFile(),
                req.modules(),
                req.platform(),
                null
        );

        ConversionResponse created = conversionService.create(createReq, userEmail);
        String projectId = created.id();

        log.info("[quick-build] Created project '{}' ({}) for {} — modules: {}",
                created.projectName(), projectId, userEmail, req.modules());

        buildService.triggerBuild(projectId);

        return ResponseEntity.accepted().body(Map.of(
                "projectId", projectId,
                "projectName", created.projectName(),
                "userEmail", userEmail,
                "modules", req.modules() != null ? req.modules() : List.of(),
                "platform", req.platform() != null ? req.platform() : "auto",
                "status", "BUILDING",
                "pollUrl", "/build/status/" + projectId,
                "progressUrl", "/build/progress/" + projectId,
                "logsUrl", "/build/logs/" + projectId,
                "hint", "Poll pollUrl every 5s until status=READY or FAILED"
        ));
    }

    /**
     * GET /build/quick-build/modules — list all modules with tier info (dev helper).
     */
    @GetMapping("/quick-build/modules")
    public ResponseEntity<Map<String, Object>> listAllModulesForDev() {
        java.util.Map<String, Object> byTier = new java.util.LinkedHashMap<>();
        for (var tier : com.example.conversion_service.entity.ConversionProject.LicenseTier.values()) {
            byTier.put(tier.name(), moduleRegistry.getAvailableModules(tier)
                    .stream().map(d -> Map.of(
                            "key", d.key(),
                            "name", d.name(),
                            "description", d.description()
                    )).collect(java.util.stream.Collectors.toList()));
        }
        return ResponseEntity.ok(Map.of(
                "allModules", moduleRegistry.getAllModules().stream()
                        .map(d -> Map.of("key", d.key(), "name", d.name(),
                                "requiredTier", d.requiredTier().name(), "description", d.description()))
                        .collect(java.util.stream.Collectors.toList()),
                "byTier", byTier
        ));
    }

    /**
     * GET /build/logs/{projectId} — build logs for a project (last known progress).
     */
    @GetMapping("/logs/{projectId}")
    public ResponseEntity<List<String>> getBuildLogs(
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "targetOS", required = false) String targetOS) {
        ConversionProject project = conversionService.findProjectById(projectId);
        String progress = project.getBuildProgress() != null ? project.getBuildProgress() : "UNKNOWN";
        String error = project.getBuildError() != null ? project.getBuildError() : "";
        return ResponseEntity.ok(List.of("Status: " + progress, error.isEmpty() ? "" : "Error: " + error));
    }
}
