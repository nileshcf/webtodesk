package com.example.conversion_service.entity;



import lombok.AllArgsConstructor;

import lombok.Builder;

import lombok.Data;

import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;

import org.springframework.data.annotation.Id;

import org.springframework.data.annotation.LastModifiedDate;

import org.springframework.data.mongodb.core.mapping.Document;



import java.time.Instant;

import java.util.List;



@Document(collection = "conversions")

@Data

@NoArgsConstructor

@AllArgsConstructor

@Builder

public class ConversionProject {



    @Id

    private String id;



    private String projectName;

    private String websiteUrl;

    private String appTitle;

    private String iconFile;



    @Builder.Default

    private String currentVersion = "1.0.0";



    @Builder.Default

    private ConversionStatus status = ConversionStatus.DRAFT;



    private String createdBy; // user email from gateway header



    private String buildArtifactPath; // R2 public URL for the built artifact (nullable)

    private String buildError;        // error message if build failed (nullable)



    // ── GitHub Actions integration fields ──

    private Long githubRunId;         // GitHub Actions workflow run ID

    private String r2Key;             // R2 object key for the uploaded artifact

    private String buildProgress;     // Granular progress: DISPATCHING, QUEUED, IN_PROGRESS, DOWNLOADING_ARTIFACT, UPLOADING_R2

    private Instant buildStartedAt;   // When the build was triggered (for stale detection)



    // ── Licensing fields (nullable — existing documents stay valid) ──

    @Builder.Default

    private LicenseTier tier = LicenseTier.TRIAL;



    private Instant licenseExpiresAt; // null = never expires (LIFETIME)



    @Builder.Default

    private Integer buildCount = 0;   // total builds triggered for this project



    @Builder.Default

    private Integer maxBuilds = 4;    // quota based on tier (TRIAL=4, STARTER=120, PRO=3000, LIFETIME=unlimited)



    private String licenseId;         // UUID assigned at project creation

    // ── Module system (nullable — existing documents stay valid) ──
    private List<String> enabledModules; // keys of enabled modules, e.g. ["offline", "splash-screen"]

    private String targetPlatform;       // "win" | "linux" — set by user in wizard, overrides server env var

    @CreatedDate

    private Instant createdAt;


    @LastModifiedDate

    private Instant updatedAt;



    public enum ConversionStatus {

        DRAFT, READY, BUILDING, FAILED

    }



    public enum LicenseTier {

        TRIAL, STARTER, PRO, LIFETIME

    }

}

