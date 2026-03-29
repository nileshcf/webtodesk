package com.example.conversion_service.service;



import com.example.conversion_service.dto.*;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.exception.ProjectNotFoundException;
import com.example.conversion_service.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@Slf4j
@Service
@RequiredArgsConstructor
public class ConversionService {

    private final ConversionRepository repository;
    private final TemplateEngine templateEngine;
    private final ModuleRegistry moduleRegistry;

    @Value("${webtodesk.build.linux-target:AppImage}")
    private String linuxTarget;



    public ConversionResponse create(CreateConversionRequest request, String createdBy) {

        log.info("Creating conversion project '{}' for user: {}", request.projectName(), createdBy);



        ConversionProject project = ConversionProject.builder()

                .projectName(sanitizeProjectName(request.projectName()))

                .websiteUrl(request.websiteUrl())

                .appTitle(request.appTitle())

                .iconFile(request.iconFile() != null ? request.iconFile() : "icon.ico")

                .createdBy(createdBy)

                .enabledModules(request.enabledModules())

                .targetPlatform(request.targetPlatform())

                .build();



        ConversionProject saved = repository.save(project);

        log.info("Conversion project created with id: {}", saved.getId());

        return ConversionResponse.from(saved);

    }



    public List<ConversionResponse> listByUser(String userEmail) {

        return repository.findByCreatedByOrderByCreatedAtDesc(userEmail)

                .stream()

                .map(ConversionResponse::from)

                .collect(Collectors.toList());

    }



    public ConversionResponse getById(String id) {

        return ConversionResponse.from(findOrThrow(id));

    }



    public ConversionResponse update(String id, UpdateConversionRequest request) {

        ConversionProject project = findOrThrow(id);



        if (request.projectName() != null) project.setProjectName(sanitizeProjectName(request.projectName()));

        if (request.websiteUrl() != null) project.setWebsiteUrl(request.websiteUrl());

        if (request.appTitle() != null) project.setAppTitle(request.appTitle());

        if (request.iconFile() != null) project.setIconFile(request.iconFile());

        if (request.currentVersion() != null) project.setCurrentVersion(request.currentVersion());

        if (request.enabledModules() != null) project.setEnabledModules(request.enabledModules());

        if (request.targetPlatform() != null) project.setTargetPlatform(request.targetPlatform());



        return ConversionResponse.from(repository.save(project));

    }



    public void delete(String id) {

        if (!repository.existsById(id)) {

            throw new ProjectNotFoundException(id);

        }

        repository.deleteById(id);

        log.info("Deleted conversion project: {}", id);

    }



    /**

     * Generates the complete Electron project files for a conversion project.

     * Returns file name → file content map.

     */

    public ElectronConfigResponse generateElectronProject(String id) {

        ConversionProject project = findOrThrow(id);

        project.setStatus(ConversionStatus.READY);
        repository.save(project);

        Map<String, String> files = buildElectronFiles(project);

        log.info("Generated Electron project for: {}", project.getProjectName());

        return new ElectronConfigResponse(
                project.getProjectName(),
                project.getAppTitle(),
                project.getWebsiteUrl(),
                files
        );
    }

    private Map<String, String> buildElectronFiles(ConversionProject project) {
        LicenseTier tier = project.getTier() != null ? project.getTier() : LicenseTier.TRIAL;
        List<String> resolved = moduleRegistry.resolveEnabledModules(project.getEnabledModules(), tier);

        StringBuilder requires = new StringBuilder();
        StringBuilder setups   = new StringBuilder();
        for (String key : resolved) {
            String varName = key.replace('-', '_');
            requires.append("const ").append(varName)
                    .append(" = require('./modules/").append(key).append("');\n");
            setups.append("  ").append(varName).append(".setup(mainWindow, config);\n");
        }

        String modulesJson = resolved.isEmpty() ? "[]"
                : "[\"" + String.join("\",\"", resolved) + "\"]";

        String targetPlatform = project.getTargetPlatform();
        boolean isWin  = targetPlatform != null && (targetPlatform.startsWith("win"));
        boolean isMac  = targetPlatform != null && (targetPlatform.startsWith("mac") || targetPlatform.startsWith("darwin"));
        boolean isLinux = !isWin && !isMac;

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("projectName",    project.getProjectName());
        ctx.put("npmPackageName", sanitizeNpmPackageName(project.getProjectName()));
        ctx.put("appId",          buildAppId(project.getProjectName()));
        ctx.put("currentVersion", project.getCurrentVersion() != null ? project.getCurrentVersion() : "1.0.0");
        ctx.put("appTitle",       project.getAppTitle());
        ctx.put("websiteUrl",     project.getWebsiteUrl());
        ctx.put("iconFile",       project.getIconFile() != null ? project.getIconFile() : "icon.ico");
        ctx.put("modulesJson",    modulesJson);
        ctx.put("hasModules",      !resolved.isEmpty());
        ctx.put("hasScreenProtect", resolved.contains("screen-protect"));
        ctx.put("moduleRequires",  requires.toString());
        ctx.put("moduleSetups",    setups.toString());
        ctx.put("isWin",   isWin);
        ctx.put("isLinux", isLinux);
        ctx.put("isMac",   isMac);
        ctx.put("linuxTarget", linuxTarget);

        Map<String, String> files = new LinkedHashMap<>();
        files.put("config.js",    templateEngine.render("config.mustache",  ctx));
        files.put("main.js",      templateEngine.render("main.mustache",    ctx));
        files.put("preload.js",   templateEngine.render("preload.mustache", ctx));
        files.put("package.json", templateEngine.render("package.mustache", ctx));

        for (String key : resolved) {
            moduleRegistry.get(key).ifPresent(def ->
                    files.put("modules/" + key + ".js",
                            templateEngine.render(def.templateFile(), Map.of())));
        }
        return files;
    }

    private static String sanitizeNpmPackageName(String name) {
        String base = name == null ? "" : name.trim().toLowerCase();
        base = base.replaceAll("\\s+", "-").replaceAll("[^a-z0-9._-]", "-")
                   .replaceAll("-{2,}", "-").replaceAll("^[._-]+", "").replaceAll("[._-]+$", "");
        if (base.isBlank()) return "webtodesk-app";
        return base.length() > 214 ? base.substring(0, 214) : base;
    }

    private static String buildAppId(String name) {
        String base = name == null ? "" : name.trim().toLowerCase();
        base = base.replaceAll("[^a-z0-9]+", ".").replaceAll("\\.{2,}", ".")
                   .replaceAll("^\\.", "").replaceAll("\\.$", "");
        return base.isBlank() ? "com.webtodesk.app" : "com.webtodesk." + base;
    }



    /**

     * Exposes the raw entity for build status checks. Used by controller for BuildStatusResponse.

     */

    public ConversionProject findProjectById(String id) {

        return findOrThrow(id);

    }



    // ─── Private Helpers ──────────────────────────────────────

    private ConversionProject findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private String sanitizeProjectName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9\\-]", "-");
    }



}

