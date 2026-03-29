package com.example.conversion_service.controller;

import com.example.conversion_service.dto.*;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.exception.ProjectNotFoundException;
import com.example.conversion_service.service.BuildService;
import com.example.conversion_service.service.ConversionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversionControllerTest {

    @Mock
    private ConversionService conversionService;

    @Mock
    private BuildService buildService;

    @InjectMocks
    private ConversionController controller;

    private ConversionResponse sampleResponse() {
        return new ConversionResponse(
                "proj-123", "my-app", "https://example.com", "My App",
                "icon.ico", "1.0.0", ConversionStatus.DRAFT,
                "user@example.com", null, false, null, null, Instant.now(), Instant.now(),
                null, null
        );
    }

    // ─── Health ──────────────────────────────────────────

    @Test
    void health_shouldReturnUp() {
        HealthController hc = new HealthController();
        ResponseEntity<Map<String, String>> response = hc.health();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    // ─── Create ──────────────────────────────────────────

    @Test
    void create_shouldDelegateToServiceAndReturn200() {
        var request = new CreateConversionRequest("My App", "https://example.com", "My App", null, null, null, null);
        when(conversionService.create(any(), eq("user@test.com"))).thenReturn(sampleResponse());

        ResponseEntity<ConversionResponse> response = controller.create(request, "user@test.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo("proj-123");
    }

    // ─── List ────────────────────────────────────────────

    @Test
    void list_shouldReturnUserProjects() {
        when(conversionService.listByUser("user@test.com")).thenReturn(List.of(sampleResponse()));

        ResponseEntity<List<ConversionResponse>> response = controller.list("user@test.com");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    // ─── Get by ID ───────────────────────────────────────

    @Test
    void getById_shouldReturnProject() {
        when(conversionService.getById("proj-123")).thenReturn(sampleResponse());

        ResponseEntity<ConversionResponse> response = controller.getById("proj-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo("proj-123");
    }

    @Test
    void getById_shouldThrow404WhenNotFound() {
        when(conversionService.getById("nonexistent")).thenThrow(new ProjectNotFoundException("nonexistent"));

        assertThatThrownBy(() -> controller.getById("nonexistent"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ─── Update ──────────────────────────────────────────

    @Test
    void update_shouldReturnUpdatedProject() {
        var updated = new ConversionResponse(
                "proj-123", "my-app", "https://new-site.com", "New Title",
                "icon.ico", "2.0.0", ConversionStatus.DRAFT,
                "user@example.com", null, false, null, null, Instant.now(), Instant.now(),
                null, null
        );
        when(conversionService.update(eq("proj-123"), any())).thenReturn(updated);

        var request = new UpdateConversionRequest(null, "https://new-site.com", "New Title", null, "2.0.0", null, null, null);
        ResponseEntity<ConversionResponse> response = controller.update("proj-123", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().websiteUrl()).isEqualTo("https://new-site.com");
    }

    // ─── Delete ──────────────────────────────────────────

    @Test
    void delete_shouldReturn204() {
        doNothing().when(conversionService).delete("proj-123");

        ResponseEntity<Void> response = controller.delete("proj-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(conversionService).delete("proj-123");
    }

    @Test
    void delete_shouldThrow404WhenNotFound() {
        doThrow(new ProjectNotFoundException("nonexistent")).when(conversionService).delete("nonexistent");

        assertThatThrownBy(() -> controller.delete("nonexistent"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ─── Generate ────────────────────────────────────────

    @Test
    void generate_shouldReturnElectronFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("config.js", "module.exports = {};");
        files.put("main.js", "const { app } = require('electron');");
        files.put("preload.js", "const { contextBridge } = require('electron');");
        files.put("package.json", "{\"name\":\"my-app\"}");

        var config = new ElectronConfigResponse("my-app", "My App", "https://example.com", files);
        when(conversionService.generateElectronProject("proj-123")).thenReturn(config);

        ResponseEntity<ElectronConfigResponse> response = controller.generate("proj-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().files()).hasSize(4);
        assertThat(response.getBody().files()).containsKeys("config.js", "main.js", "preload.js", "package.json");
    }

    @Test
    void generate_shouldThrow404WhenNotFound() {
        when(conversionService.generateElectronProject("nonexistent"))
                .thenThrow(new ProjectNotFoundException("nonexistent"));

        assertThatThrownBy(() -> controller.generate("nonexistent"))
                .isInstanceOf(ProjectNotFoundException.class);
    }
}
