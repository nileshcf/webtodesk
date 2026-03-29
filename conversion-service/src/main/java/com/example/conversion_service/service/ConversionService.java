package com.example.conversion_service.service;



import com.example.conversion_service.dto.*;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.exception.LicenseViolationException;
import com.example.conversion_service.exception.ProjectNotFoundException;
import com.example.conversion_service.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.conversion_service.dto.ModuleConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
    private final LicenseService licenseService;

    @Value("${webtodesk.build.linux-target:AppImage}")
    private String linuxTarget;



    public ConversionResponse create(CreateConversionRequest request, String createdBy) {

        log.info("Creating conversion project '{}' for user: {}", request.projectName(), createdBy);

        // Derive the user's best tier and hard-enforce module access
        LicenseTier userTier = licenseService.getUserBestTier(createdBy);
        validateModuleTierAccess(request.enabledModules(), userTier);

        ConversionProject project = ConversionProject.builder()

                .projectName(sanitizeProjectName(request.projectName()))

                .websiteUrl(request.websiteUrl())

                .appTitle(request.appTitle())

                .iconFile(request.iconFile() != null ? request.iconFile() : "icon.ico")

                .createdBy(createdBy)

                .enabledModules(request.enabledModules())

                .targetPlatform(request.targetPlatform())

                .moduleConfig(request.moduleConfig())

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

        // Enforce tier gating when modules are being changed
        if (request.enabledModules() != null) {
            LicenseTier tier = project.getTier() != null ? project.getTier() : LicenseTier.TRIAL;
            validateModuleTierAccess(request.enabledModules(), tier);
        }

        if (request.projectName() != null) project.setProjectName(sanitizeProjectName(request.projectName()));

        if (request.websiteUrl() != null) project.setWebsiteUrl(request.websiteUrl());

        if (request.appTitle() != null) project.setAppTitle(request.appTitle());

        if (request.iconFile() != null) project.setIconFile(request.iconFile());

        if (request.currentVersion() != null) project.setCurrentVersion(request.currentVersion());

        if (request.enabledModules() != null) project.setEnabledModules(request.enabledModules());

        if (request.targetPlatform() != null) project.setTargetPlatform(request.targetPlatform());

        if (request.moduleConfig() != null) project.setModuleConfig(request.moduleConfig());



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

        StringBuilder requires       = new StringBuilder();
        StringBuilder setups         = new StringBuilder();
        StringBuilder preloadReqs    = new StringBuilder();
        StringBuilder preloadSetups  = new StringBuilder();
        for (String key : resolved) {
            String varName = key.replace('-', '_');
            requires.append("const ").append(varName)
                    .append(" = require('./modules/").append(key).append("');\n");
            setups.append("  ").append(varName).append(".setup(mainWindow, config);\n");
            preloadReqs.append("const ").append(varName)
                    .append(" = require('./modules/").append(key).append("');\n");
            preloadSetups.append("  ").append(varName)
                    .append(".preloadSetup(contextBridge, ipcRenderer, config);\n");
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
        ctx.put("preloadModuleRequires", preloadReqs.toString());
        ctx.put("preloadModuleSetups",   preloadSetups.toString());
        ctx.put("isWin",   isWin);
        ctx.put("isLinux", isLinux);
        ctx.put("isMac",   isMac);
        ctx.put("linuxTarget", linuxTarget);
        applyModuleConfigContext(ctx, resolved, project.getModuleConfig());

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

    private static void applyModuleConfigContext(Map<String, Object> ctx, List<String> resolved, ModuleConfig mc) {
        boolean hasDomainLock = resolved.contains("domain-lock");
        ctx.put("hasDomainLock", hasDomainLock);
        if (hasDomainLock) {
            ModuleConfig.DomainLockConfig dlc = (mc != null && mc.getDomainLock() != null)
                    ? mc.getDomainLock() : new ModuleConfig.DomainLockConfig();
            ctx.put("domainLockConfigJson", toJson(dlc));
        }

        boolean hasTitleBar = resolved.contains("title-bar");
        ctx.put("hasTitleBar", hasTitleBar);
        if (hasTitleBar) {
            ModuleConfig.TitleBarConfig tbc = (mc != null && mc.getTitleBar() != null)
                    ? mc.getTitleBar() : new ModuleConfig.TitleBarConfig();
            ctx.put("titleBarConfigJson", toJson(tbc));
        }

        boolean hasWatermark = resolved.contains("watermark");
        ctx.put("hasWatermark", hasWatermark);
        if (hasWatermark) {
            ModuleConfig.WatermarkConfig wmc = (mc != null && mc.getWatermark() != null)
                    ? mc.getWatermark() : new ModuleConfig.WatermarkConfig();
            if (wmc.getExpiresAt() == null && mc != null && mc.getExpiry() != null) {
                wmc.setExpiresAt(mc.getExpiry().getExpiresAt());
            }
            ctx.put("watermarkConfigJson", toJson(wmc));
        }

        boolean hasExpiry = resolved.contains("expiry");
        ctx.put("hasExpiry", hasExpiry);
        if (hasExpiry) {
            ModuleConfig.ExpiryConfig ec = (mc != null && mc.getExpiry() != null)
                    ? mc.getExpiry() : new ModuleConfig.ExpiryConfig();
            ctx.put("expiryConfigJson", toJson(ec));
        }

        ctx.put("hasNotifications", resolved.contains("notifications"));
        ctx.put("hasDarkMode",      resolved.contains("dark-mode"));

        boolean hasSystemTray = resolved.contains("system-tray");
        ctx.put("hasSystemTray", hasSystemTray);
        if (hasSystemTray) {
            ModuleConfig.SystemTrayConfig stc = (mc != null && mc.getSystemTray() != null)
                    ? mc.getSystemTray() : new ModuleConfig.SystemTrayConfig();
            ctx.put("systemTrayConfigJson", toJson(stc));
        }

        boolean hasRightClick = resolved.contains("right-click");
        ctx.put("hasRightClick", hasRightClick);
        if (hasRightClick) {
            ModuleConfig.RightClickConfig rcc = (mc != null && mc.getRightClick() != null)
                    ? mc.getRightClick() : new ModuleConfig.RightClickConfig();
            ctx.put("rightClickConfigJson", toJson(rcc));
        }

        boolean hasAutoUpdate = resolved.contains("auto-update");
        ctx.put("hasAutoUpdate", hasAutoUpdate);
        if (hasAutoUpdate) {
            ModuleConfig.AutoUpdateConfig auc = (mc != null && mc.getAutoUpdate() != null)
                    ? mc.getAutoUpdate() : new ModuleConfig.AutoUpdateConfig();
            ctx.put("autoUpdateConfigJson", toJson(auc));
        }

        boolean hasKeyBindings = resolved.contains("key-bindings");
        ctx.put("hasKeyBindings", hasKeyBindings);
        if (hasKeyBindings) {
            ModuleConfig.KeyBindingsConfig kbc = (mc != null && mc.getKeyBindings() != null)
                    ? mc.getKeyBindings() : new ModuleConfig.KeyBindingsConfig();
            ctx.put("keyBindingsConfigJson", toJson(kbc));
        }

        boolean hasWindowPolish = resolved.contains("window-polish");
        ctx.put("hasWindowPolish", hasWindowPolish);
        if (hasWindowPolish) {
            ModuleConfig.WindowPolishConfig wpc = (mc != null && mc.getWindowPolish() != null)
                    ? mc.getWindowPolish() : new ModuleConfig.WindowPolishConfig();
            ctx.put("windowPolishConfigJson", toJson(wpc));
        }

        boolean hasClipboard = resolved.contains("clipboard");
        ctx.put("hasClipboard", hasClipboard);
        if (hasClipboard) {
            ModuleConfig.ClipboardConfig cc = (mc != null && mc.getClipboard() != null)
                    ? mc.getClipboard() : new ModuleConfig.ClipboardConfig();
            ctx.put("clipboardConfigJson", toJson(cc));
        }
    }

    private static String toJson(Object obj) {
        try {
            return new ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
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



    /** Aggregate stats for a user's dashboard. */
    public ConversionStatsResponse getStats(String userEmail) {
        List<ConversionProject> projects = repository.findByCreatedByOrderByCreatedAtDesc(userEmail);

        int total    = projects.size();
        int draft    = (int) projects.stream().filter(p -> p.getStatus() == ConversionStatus.DRAFT).count();
        int ready    = (int) projects.stream().filter(p -> p.getStatus() == ConversionStatus.READY).count();
        int building = (int) projects.stream().filter(p -> p.getStatus() == ConversionStatus.BUILDING).count();
        int failed   = (int) projects.stream().filter(p -> p.getStatus() == ConversionStatus.FAILED).count();
        int totalBuilds = projects.stream()
                .mapToInt(p -> p.getBuildCount() != null ? p.getBuildCount() : 0).sum();

        LicenseInfoResponse license = licenseService.getCurrentLicense(userEmail);
        int allowed   = license.buildsAllowed();
        int remaining = Math.max(0, allowed - totalBuilds);

        return new ConversionStatsResponse(
                userEmail, total, draft, ready, building, failed,
                totalBuilds, allowed, remaining,
                license.tier(), license.licenseExpiresAt());
    }

    /**

     * Exposes the raw entity for build status checks. Used by controller for BuildStatusResponse.

     */

    public ConversionProject findProjectById(String id) {

        return findOrThrow(id);

    }



    // ─── Private Helpers ──────────────────────────────────────

    /**
     * Throws {@link LicenseViolationException} if any requested module key requires
     * a higher tier than the user currently holds.
     */
    private void validateModuleTierAccess(List<String> requestedModules, LicenseTier tier) {
        if (requestedModules == null || requestedModules.isEmpty()) return;
        List<String> blocked = requestedModules.stream()
                .filter(key -> !moduleRegistry.isAvailable(key, tier))
                .filter(key -> moduleRegistry.get(key).isPresent()) // only known modules
                .toList();
        if (!blocked.isEmpty()) {
            log.warn("DEV MODE BYPASS: Module(s) {} require a higher tier than {}. Allowing for local testing.", blocked, tier);
            // throw new LicenseViolationException(
            //         "Module(s) " + blocked + " require a higher tier than " + tier +
            //         ". Upgrade to PRO or LIFETIME to enable these features.");
        }
    }

    private ConversionProject findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private String sanitizeProjectName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9\\-]", "-");
    }



}

