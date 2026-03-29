package com.example.conversion_service.service;

import com.example.conversion_service.dto.ConversionResponse;
import com.example.conversion_service.dto.VersionBumpResponse;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.exception.ProjectNotFoundException;
import com.example.conversion_service.repository.ConversionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionUpgradeServiceTest {

    @Mock
    private ConversionRepository repository;

    @Mock
    private ConversionService conversionService;

    @InjectMocks
    private VersionUpgradeService service;

    private ConversionProject project;
    private ConversionResponse projectResponse;

    @BeforeEach
    void setUp() {
        project = ConversionProject.builder()
                .id("proj-1")
                .projectName("My App")
                .websiteUrl("https://example.com")
                .appTitle("My App")
                .currentVersion("1.2.3")
                .status(ConversionStatus.READY)
                .tier(LicenseTier.TRIAL)
                .buildCount(2)
                .maxBuilds(4)
                .createdBy("user@example.com")
                .build();

        projectResponse = new ConversionResponse(
                "proj-1", "My App", "https://example.com", "My App",
                null, "1.2.4", ConversionStatus.DRAFT, "user@example.com",
                null, false, null, null, Instant.now(), Instant.now(), null, null
        );
    }

    // ── bumpVersion ──────────────────────────────────────────────────────────

    @Test
    void bumpVersion_patch_updatesVersionAndResetsToDraft() {
        when(repository.findById("proj-1")).thenReturn(Optional.of(project));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversionService.getById("proj-1")).thenReturn(projectResponse);

        VersionBumpResponse result = service.bumpVersion("proj-1", "patch");

        assertThat(result.previousVersion()).isEqualTo("1.2.3");
        assertThat(result.newVersion()).isEqualTo("1.2.4");
        assertThat(result.bumpType()).isEqualTo("patch");
        assertThat(result.projectId()).isEqualTo("proj-1");

        verify(repository).save(argThat(p ->
                "1.2.4".equals(p.getCurrentVersion()) &&
                p.getStatus() == ConversionStatus.DRAFT &&
                p.getBuildArtifactPath() == null &&
                p.getBuildError() == null
        ));
    }

    @Test
    void bumpVersion_minor_resetsMinorAndPatch() {
        when(repository.findById("proj-1")).thenReturn(Optional.of(project));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversionService.getById("proj-1")).thenReturn(projectResponse);

        VersionBumpResponse result = service.bumpVersion("proj-1", "minor");

        assertThat(result.previousVersion()).isEqualTo("1.2.3");
        assertThat(result.newVersion()).isEqualTo("1.3.0");
    }

    @Test
    void bumpVersion_major_resetsAllComponents() {
        when(repository.findById("proj-1")).thenReturn(Optional.of(project));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversionService.getById("proj-1")).thenReturn(projectResponse);

        VersionBumpResponse result = service.bumpVersion("proj-1", "major");

        assertThat(result.previousVersion()).isEqualTo("1.2.3");
        assertThat(result.newVersion()).isEqualTo("2.0.0");
    }

    @Test
    void bumpVersion_projectNotFound_throwsException() {
        when(repository.findById("no-such")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bumpVersion("no-such", "patch"))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessageContaining("no-such");
    }

    @Test
    void bumpVersion_nullCurrentVersion_defaultsTo1_0_0ThenBumps() {
        project.setCurrentVersion(null);
        when(repository.findById("proj-1")).thenReturn(Optional.of(project));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(conversionService.getById("proj-1")).thenReturn(projectResponse);

        VersionBumpResponse result = service.bumpVersion("proj-1", "patch");

        assertThat(result.newVersion()).isEqualTo("1.0.1");
    }

    // ── incrementVersion (pure logic) ────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "1.0.0,  patch, 1.0.1",
        "1.0.9,  patch, 1.0.10",
        "2.4.7,  patch, 2.4.8",
        "1.0.0,  minor, 1.1.0",
        "1.9.5,  minor, 1.10.0",
        "1.0.0,  major, 2.0.0",
        "9.9.9,  major, 10.0.0",
        "1.2,    patch, 1.2.1",
        "3,      minor, 3.1.0",
        "1.2.3-SNAPSHOT, patch, 1.2.4",
    })
    void incrementVersion_covers_all_combinations(String current, String type, String expected) {
        assertThat(service.incrementVersion(current.trim(), type.trim())).isEqualTo(expected);
    }

    @Test
    void incrementVersion_null_returnsDefaults() {
        assertThat(service.incrementVersion(null, "major")).isEqualTo("2.0.0");
        assertThat(service.incrementVersion(null, "minor")).isEqualTo("1.1.0");
        assertThat(service.incrementVersion(null, "patch")).isEqualTo("1.0.1");
    }

    @Test
    void incrementVersion_blank_returnsDefaults() {
        assertThat(service.incrementVersion("", "major")).isEqualTo("2.0.0");
        assertThat(service.incrementVersion("  ", "patch")).isEqualTo("1.0.1");
    }
}
