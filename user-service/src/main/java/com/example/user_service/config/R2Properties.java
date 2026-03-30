package com.example.user_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "webtodesk.r2")
public class R2Properties {
    private boolean enabled = true;
    private String accountId;
    private String accessKeyId;
    private String secretAccessKey;
    private String bucket;
    private String publicUrl;
    private String endpoint;
}
