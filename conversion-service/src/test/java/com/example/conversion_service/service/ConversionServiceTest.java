package com.example.conversion_service.service;

import com.example.conversion_service.dto.*;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.exception.ProjectNotFoundException;
import com.example.conversion_service.repository.ConversionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversionServiceTest {

    @Mock
    private ConversionRepository repository;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private ModuleRegistry moduleRegistry;

    @InjectMocks
    private ConversionService conversionService;

    private ConversionProject sampleProject;

    @BeforeEach
    void setUp() {
        // Delegate templateEngine.render() to real implementation so file-content tests work
        TemplateEngine realEngine = new TemplateEngine();
        lenient().when(templateEngine.render(anyString(), any()))
                .thenAnswer(inv -> realEngine.render(inv.getArgument(0), inv.getArgument(1)));
        // moduleRegistry returns empty modules by default (no modules enabled)
        lenient().when(moduleRegistry.resolveEnabledModules(any(), any()))
                .thenReturn(Collections.emptyList());

        sampleProject = ConversionProject.builder()
                .id("proj-123")
                .projectName("my-test-app")
                .websiteUrl("https://example.com")
                .appTitle("My Test App")
                .iconFile("icon.ico")
                .currentVersion("1.0.0")
                .status(ConversionStatus.DRAFT)
                .createdBy("user@example.com")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void create_shouldSaveAndReturnResponse() {
        var request = new CreateConversionRequest("My Test App", "https://example.com", "My Test App", null, null, null, null);
        when(repository.save(any(ConversionProject.class))).thenReturn(sampleProject);

        ConversionResponse response = conversionService.create(request, "user@example.com");

        assertThat(response.id()).isEqualTo("proj-123");
        assertThat(response.appTitle()).isEqualTo("My Test App");
        assertThat(response.status()).isEqualTo(ConversionStatus.DRAFT);
        verify(repository).save(any(ConversionProject.class));
    }

    @Test
    void create_shouldSanitizeProjectName() {
        var request = new CreateConversionRequest("My Cool App!", "https://example.com", "Cool App", null, null, null, null);
        when(repository.save(any(ConversionProject.class))).thenAnswer(inv -> {
            ConversionProject saved = inv.getArgument(0);
            saved.setId("proj-456");
            return saved;
        });

        ConversionResponse response = conversionService.create(request, "user@example.com");

        assertThat(response.projectName()).isEqualTo("my-cool-app-");
    }

    @Test
    void create_shouldDefaultIconFile() {
        var request = new CreateConversionRequest("Test", "https://example.com", "Test", null, null, null, null);
        when(repository.save(any(ConversionProject.class))).thenAnswer(inv -> inv.getArgument(0));

        conversionService.create(request, "user@example.com");

        verify(repository).save(argThat(project -> "icon.ico".equals(project.getIconFile())));
    }

    @Test
    void listByUser_shouldReturnUserProjects() {
        when(repository.findByCreatedByOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(sampleProject));

        List<ConversionResponse> result = conversionService.listByUser("user@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).createdBy()).isEqualTo("user@example.com");
    }

    @Test
    void listByUser_shouldReturnEmptyListWhenNoProjects() {
        when(repository.findByCreatedByOrderByCreatedAtDesc("nobody@example.com"))
                .thenReturn(List.of());

        List<ConversionResponse> result = conversionService.listByUser("nobody@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void getById_shouldReturnProject() {
        when(repository.findById("proj-123")).thenReturn(Optional.of(sampleProject));

        ConversionResponse response = conversionService.getById("proj-123");

        assertThat(response.id()).isEqualTo("proj-123");
        assertThat(response.websiteUrl()).isEqualTo("https://example.com");
    }

    @Test
    void getById_shouldThrowWhenNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.getById("nonexistent"))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void update_shouldModifyFields() {
        when(repository.findById("proj-123")).thenReturn(Optional.of(sampleProject));
        when(repository.save(any(ConversionProject.class))).thenReturn(sampleProject);

        var request = new UpdateConversionRequest(null, "https://new-url.com", "New Title", null, "2.0.0", null, null, null);
        conversionService.update("proj-123", request);

        verify(repository).save(argThat(project ->
                "https://new-url.com".equals(project.getWebsiteUrl()) &&
                "New Title".equals(project.getAppTitle()) &&
                "2.0.0".equals(project.getCurrentVersion())
        ));
    }

    @Test
    void update_shouldThrowWhenNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        var request = new UpdateConversionRequest("name", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> conversionService.update("nonexistent", request))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void delete_shouldDeleteExistingProject() {
        when(repository.existsById("proj-123")).thenReturn(true);

        conversionService.delete("proj-123");

        verify(repository).deleteById("proj-123");
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(repository.existsById("nonexistent")).thenReturn(false);

        assertThatThrownBy(() -> conversionService.delete("nonexistent"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void generateElectronProject_shouldReturnFourFiles() {
        when(repository.findById("proj-123")).thenReturn(Optional.of(sampleProject));
        when(repository.save(any(ConversionProject.class))).thenReturn(sampleProject);

        ElectronConfigResponse response = conversionService.generateElectronProject("proj-123");

        assertThat(response.files()).hasSize(4);
        assertThat(response.files()).containsKeys("config.js", "main.js", "preload.js", "package.json");
        assertThat(response.projectName()).isEqualTo("my-test-app");
        assertThat(response.appTitle()).isEqualTo("My Test App");
        assertThat(response.websiteUrl()).isEqualTo("https://example.com");
    }

    @Test
    void generateElectronProject_shouldSetStatusToReady() {
        when(repository.findById("proj-123")).thenReturn(Optional.of(sampleProject));
        when(repository.save(any(ConversionProject.class))).thenReturn(sampleProject);

        conversionService.generateElectronProject("proj-123");

        verify(repository).save(argThat(project ->
                project.getStatus() == ConversionStatus.READY
        ));
    }

    @Test
    void generateElectronProject_shouldThrowWhenNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.generateElectronProject("nonexistent"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void generateElectronProject_configJsShouldContainProjectDetails() {
        when(repository.findById("proj-123")).thenReturn(Optional.of(sampleProject));
        when(repository.save(any(ConversionProject.class))).thenReturn(sampleProject);

        ElectronConfigResponse response = conversionService.generateElectronProject("proj-123");

        String configJs = response.files().get("config.js");
        assertThat(configJs).contains("my-test-app");
        assertThat(configJs).contains("https://example.com");
        assertThat(configJs).contains("My Test App");
        assertThat(configJs).contains("1.0.0");
    }

    @Test
    void generateElectronProject_mainJsShouldHaveSecurityDefaults() {
        when(repository.findById("proj-123")).thenReturn(Optional.of(sampleProject));
        when(repository.save(any(ConversionProject.class))).thenReturn(sampleProject);

        ElectronConfigResponse response = conversionService.generateElectronProject("proj-123");

        String mainJs = response.files().get("main.js");
        assertThat(mainJs).contains("nodeIntegration: false");
        assertThat(mainJs).contains("contextIsolation: true");
        assertThat(mainJs).contains("show: false");
        assertThat(mainJs).contains("ready-to-show");
    }

    @Test
    void generateElectronProject_preloadJsShouldUseContextBridge() {
        when(repository.findById("proj-123")).thenReturn(Optional.of(sampleProject));
        when(repository.save(any(ConversionProject.class))).thenReturn(sampleProject);

        ElectronConfigResponse response = conversionService.generateElectronProject("proj-123");

        String preloadJs = response.files().get("preload.js");
        assertThat(preloadJs).contains("contextBridge.exposeInMainWorld");
    }

    @Test
    void generateElectronProject_packageJsonShouldContainProjectMetadata() {
        when(repository.findById("proj-123")).thenReturn(Optional.of(sampleProject));
        when(repository.save(any(ConversionProject.class))).thenReturn(sampleProject);

        ElectronConfigResponse response = conversionService.generateElectronProject("proj-123");

        String packageJson = response.files().get("package.json");
        assertThat(packageJson).contains("\"name\": \"my-test-app\"");
        assertThat(packageJson).contains("\"version\": \"1.0.0\"");
        assertThat(packageJson).contains("electron-builder");
    }
}
