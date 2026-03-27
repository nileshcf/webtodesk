package com.example.conversion_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {
    private String token;
    private String owner;
    private String repo;
    private String workflow;
}
