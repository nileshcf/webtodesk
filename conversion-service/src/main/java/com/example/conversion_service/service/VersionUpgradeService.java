package com.example.conversion_service.service;

import com.example.conversion_service.dto.ConversionResponse;
import com.example.conversion_service.dto.VersionBumpResponse;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.exception.ProjectNotFoundException;
import com.example.conversion_service.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionUpgradeService {

    private final ConversionRepository repository;
    private final ConversionService conversionService;

    /**
     * Bumps the semantic version (major / minor / patch) of a project and resets
     * its status to DRAFT so a fresh build can be triggered for the new version.
     *
     * @param projectId  the project to bump
     * @param bumpType   "major" | "minor" | "patch"
     * @return           the bump result including old and new version strings
     */
    public VersionBumpResponse bumpVersion(String projectId, String bumpType) {
        ConversionProject project = repository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + projectId));

        String previous = project.getCurrentVersion() != null ? project.getCurrentVersion() : "1.0.0";
        String next = incrementVersion(previous, bumpType);

        project.setCurrentVersion(next);
        project.setStatus(ConversionStatus.DRAFT);
        project.setBuildArtifactPath(null);
        project.setBuildError(null);
        project.setBuildProgress(null);
        project.setBuildStartedAt(null);

        repository.save(project);
        log.info("Version bumped for project {} ({} → {} via {})", projectId, previous, next, bumpType);

        ConversionResponse response = conversionService.getById(projectId);
        return new VersionBumpResponse(projectId, previous, next, bumpType, response);
    }

    /**
     * Parses a SemVer string and returns the next version for the given bump type.
     * Supports "X", "X.Y", and "X.Y.Z" formats. Non-numeric pre-release suffixes
     * are stripped before incrementing.
     */
    public String incrementVersion(String current, String bumpType) {
        if (current == null || current.isBlank()) {
            return switch (bumpType) {
                case "major" -> "2.0.0";
                case "minor" -> "1.1.0";
                default      -> "1.0.1";
            };
        }

        // Strip any pre-release or build suffix (e.g. "1.2.3-SNAPSHOT" → "1.2.3")
        String clean = current.replaceAll("-.*$", "").trim();
        String[] parts = clean.split("\\.");

        int major = parseIntSafe(parts, 0);
        int minor = parseIntSafe(parts, 1);
        int patch = parseIntSafe(parts, 2);

        return switch (bumpType) {
            case "major" -> (major + 1) + ".0.0";
            case "minor" -> major + "." + (minor + 1) + ".0";
            default      -> major + "." + minor + "." + (patch + 1);
        };
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private int parseIntSafe(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
