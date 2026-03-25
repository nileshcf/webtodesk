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

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public enum ConversionStatus {
        DRAFT, READY, BUILDING, FAILED
    }
}
