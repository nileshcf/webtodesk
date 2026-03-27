package com.example.conversion_service.controller;

import com.example.conversion_service.dto.*;
import com.example.conversion_service.service.BuildService;
import com.example.conversion_service.service.ConversionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/conversions")
@RequiredArgsConstructor
public class ConversionController {

    private final ConversionService conversionService;
    private final BuildService buildService;

    @PostMapping
    public ResponseEntity<ConversionResponse> create(
            @RequestBody @Valid CreateConversionRequest request,
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(conversionService.create(request, userEmail));
    }

    @GetMapping
    public ResponseEntity<List<ConversionResponse>> list(
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(conversionService.listByUser(userEmail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversionResponse> getById(@PathVariable("id") String id) {
        return ResponseEntity.ok(conversionService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversionResponse> update(
            @PathVariable("id") String id,
            @RequestBody @Valid UpdateConversionRequest request) {
        return ResponseEntity.ok(conversionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        conversionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<ElectronConfigResponse> generate(@PathVariable("id") String id) {
        return ResponseEntity.ok(conversionService.generateElectronProject(id));
    }

    @PostMapping("/{id}/build")
    public ResponseEntity<BuildStatusResponse> triggerBuild(@PathVariable("id") String id) {
        ConversionResponse project = conversionService.getById(id);
        buildService.triggerBuild(id);
        return ResponseEntity.accepted().body(
                new BuildStatusResponse(id, project.projectName(),
                        com.example.conversion_service.entity.ConversionProject.ConversionStatus.BUILDING,
                        null, false, null, "PREPARING", null)
        );
    }

    @GetMapping("/{id}/build/status")
    public ResponseEntity<BuildStatusResponse> buildStatus(@PathVariable("id") String id) {
        return ResponseEntity.ok(
                BuildStatusResponse.from(conversionService.findProjectById(id))
        );
    }

    /**
     * Download redirect — sends the user to the R2 public URL.
     */
    @GetMapping("/{id}/build/download")
    public ResponseEntity<Void> downloadBuild(@PathVariable("id") String id) {
        String downloadUrl = buildService.getDownloadUrl(id);
        if (downloadUrl == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(302)
                .location(URI.create(downloadUrl))
                .build();
    }

    /**
     * SSE endpoint for real-time build progress updates.
     */
    @GetMapping("/{id}/build/stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamBuildProgress(
            @PathVariable("id") String id) {
        return buildService.createSseEmitter(id);
    }
}

