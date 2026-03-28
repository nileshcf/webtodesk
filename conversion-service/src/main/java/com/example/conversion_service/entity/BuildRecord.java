package com.example.conversion_service.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Persisted record of every build attempt for audit and history purposes.
 * One BuildRecord is saved per triggerBuild() call, regardless of outcome.
 */
@Document(collection = "build_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildRecord {

    @Id
    private String id;

    @Indexed
    private String projectId;

    private String projectName;

    @Indexed
    private String userEmail;

    private ConversionProject.LicenseTier tier;

    private String result;          // "READY" | "FAILED"

    private String buildError;      // null on success

    private String artifactUrl;     // R2 public URL, null on failure

    private String buildTarget;     // "win" | "linux" | "mac"

    private List<String> enabledModules;

    @CreatedDate
    private Instant startedAt;

    private Instant completedAt;

    private long durationMs;        // completedAt - startedAt in milliseconds
}
