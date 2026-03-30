package com.example.conversion_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI webToDeskOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WebToDesk — Conversion Service API")
                        .description("""
                                REST API for the WebToDesk conversion service.
                                Converts website URLs into packaged Electron desktop apps and manages the full
                                build pipeline: project CRUD, module selection, license enforcement, build queue,
                                Cloudflare R2 artifact delivery, and real-time SSE progress streaming.
                                """)
                        .version("v1.8.0")
                        .contact(new Contact()
                                .name("WebToDesk Team")
                                .url("https://webtodesk.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://webtodesk.com/terms")))
                .servers(List.of(
                        new Server().url("/conversion").description("Via API Gateway (development)"),
                        new Server().url("http://localhost:8082").description("Direct (local)")))
                .addSecurityItem(new SecurityRequirement().addList("gateway-jwt"))
                .components(new Components()
                        .addSecuritySchemes("gateway-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-User-Email")
                                        .description("User email injected by the API Gateway after JWT validation")));
    }
}
