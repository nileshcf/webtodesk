package com.example.conversion_service.service;

import com.example.conversion_service.entity.BuildRecord;
import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.entity.ConversionProject.LicenseTier;
import com.example.conversion_service.exception.ProjectNotFoundException;
import com.example.conversion_service.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildService {

    private final ConversionRepository repository;
    private final R2StorageService r2StorageService;
    private final LicenseService licenseService;
    private final BuildQueueService buildQueueService;
    private final TemplateEngine templateEngine;
    private final ModuleRegistry moduleRegistry;
    private final BuildMetricsService buildMetricsService;

    @Value("${webtodesk.build.output-dir:${java.io.tmpdir}/webtodesk-builds}")
    private String buildOutputDir;

    @Value("${webtodesk.build.target-platform:auto}")
    private String targetPlatform;

    @Value("${webtodesk.build.development-build:false}")
    private boolean developmentBuild;

    @Value("${webtodesk.build.keep-workspace-on-failure:false}")
    private boolean keepWorkspaceOnFailure;

    @Value("${webtodesk.build.electron-builder.debug:false}")
    private boolean electronBuilderDebug;

    @Value("${webtodesk.build.linux-target:AppImage}")
    private String linuxTarget;

    @Value("${webtodesk.build.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${webtodesk.build.retry.backoff-ms:3000}")
    private long retryBackoffMs;

    @Value("${webtodesk.build.retry.jitter-ms:750}")
    private long retryJitterMs;

    // SSE emitters per projectId — multiple clients can subscribe
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // ─── Public API ──────────────────────────────────────

    /**
     * Triggers a LOCAL build for the Electron app.
     * 0. Validate build environment (node/npm present, disk space)
     * 1. Create temp workspace
     * 2. Write generated Electron config files
     * 3. Run npm install
     * 4. Run electron-builder for the resolved target platform
     * 5. Upload the installer artifact to R2
     * 6. Stream progress via SSE throughout
     */
    @Async("buildExecutor")
    public void triggerBuild(String projectId) {
        ConversionProject project = repository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        MDC.put("projectId", projectId);
        if (project.getCreatedBy() != null) MDC.put("userEmail", project.getCreatedBy());

        // License check — throws LicenseViolationException (→ 402) if quota exceeded
        if (!developmentBuild) {
            licenseService.validateBuildRequest(project);
        } else {
            log.warn("[DEV MODE] Skipping license validation for project '{}'", project.getProjectName());
        }

        project.setStatus(ConversionStatus.BUILDING);
        project.setBuildProgress("PREPARING");
        project.setBuildStartedAt(Instant.now());
        project.setBuildError(null);
        project.setBuildArtifactPath(null);
        project.setR2Key(null);
        repository.save(project);
        emitProgress(projectId, "PREPARING", "Preparing build workspace...");

        buildQueueService.recordBuildStarted(projectId,
                project.getTier() != null ? project.getTier() : com.example.conversion_service.entity.ConversionProject.LicenseTier.TRIAL);

        Path workspace = null;
        boolean buildFailed = false;

        try {
            // 0. Validate build environment before touching disk
            validateBuildEnvironment(project);

            // 1. Create workspace
            workspace = createWorkspace(projectId);

            // 2. Write generated Electron files into workspace
            writeElectronFiles(project, workspace);

            // 3. npm install
            runNpmInstall(project, workspace);

            // 4. electron-builder for the resolved target platform
            runElectronBuilder(project, workspace);

            // 5. Find the installer artifact and upload to R2
            uploadArtifact(project, workspace);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            buildFailed = true;
            failBuild(project, "Build interrupted");
        } catch (Exception e) {
            log.error("Build failed for '{}': {}", project.getProjectName(), e.getMessage(), e);
            buildFailed = true;
            failBuild(project, e.getMessage());
        } finally {
            // 6. Cleanup workspace and release queue slot
            if (workspace != null) {
                if (buildFailed && keepWorkspaceOnFailure) {
                    log.warn("Keeping build workspace for troubleshooting: {}", workspace);
                } else {
                    cleanupWorkspace(workspace);
                }
            }
            buildQueueService.recordBuildFinished(projectId,
                    project.getTier() != null ? project.getTier() : com.example.conversion_service.entity.ConversionProject.LicenseTier.TRIAL);
            MDC.clear();
        }
    }

    /**
     * Returns the R2 download URL for the artifact, or null if not available.
     */
    public String getDownloadUrl(String projectId) {
        ConversionProject project = repository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (project.getBuildArtifactPath() != null && project.getStatus() == ConversionStatus.READY) {
            return project.getBuildArtifactPath();
        }
        return null;
    }

    /**
     * Register an SSE emitter for real-time build progress.
     */
    public SseEmitter createSseEmitter(String projectId) {
        SseEmitter emitter = new SseEmitter(900_000L); // 15 min timeout

        emitters.computeIfAbsent(projectId, k -> Collections.synchronizedList(new ArrayList<>())).add(emitter);

        emitter.onCompletion(() -> removeEmitter(projectId, emitter));
        emitter.onTimeout(() -> removeEmitter(projectId, emitter));
        emitter.onError(e -> removeEmitter(projectId, emitter));

        // Send current status immediately
        ConversionProject project = repository.findById(projectId).orElse(null);
        if (project != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(Map.of(
                                "status", project.getStatus().name(),
                                "buildProgress", project.getBuildProgress() != null ? project.getBuildProgress() : "",
                                "buildError", project.getBuildError() != null ? project.getBuildError() : "",
                                "downloadAvailable", project.getBuildArtifactPath() != null && project.getStatus() == ConversionStatus.READY,
                                "downloadUrl", project.getBuildArtifactPath() != null ? project.getBuildArtifactPath() : ""
                        )));
            } catch (IOException ignored) {}
        }

        return emitter;
    }

    // ─── Local Build Steps ───────────────────────────────

    private Path createWorkspace(String projectId) throws IOException {
        Path workspace = Paths.get(buildOutputDir, "build-" + projectId + "-" + System.currentTimeMillis());
        Files.createDirectories(workspace);
        log.info("Created build workspace: {}", workspace);
        return workspace;
    }



    private void writeElectronFiles(ConversionProject project, Path workspace) throws IOException {
        updateProgress(project, "WRITING_FILES", "Writing Electron configuration files...");

        Map<String, String> files = generateFiles(project);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path filePath = workspace.resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, entry.getValue());
            log.debug("Wrote file: {}", filePath);
        }

        log.info("Wrote {} Electron files for '{}'", files.size(), project.getProjectName());
    }

    private void runNpmInstall(ConversionProject project, Path workspace) throws IOException, InterruptedException {
        updateProgress(project, "INSTALLING", "Running npm install...");

        ProcessResult result = runProcessWithRetries(
                project.getId(),
                workspace,
                "npm install",
                command("npm", "install", "--no-audit", "--no-fund", "--prefer-offline"),
                null
        );

        if (result.exitCode() != 0) {
            throw new IOException("npm install failed with exit code " + result.exitCode() + "\n" + result.outputTail());
        }

        log.info("npm install completed for '{}'", project.getProjectName());
    }

    private void runElectronBuilder(ConversionProject project, Path workspace) throws IOException, InterruptedException {
        BuildTarget buildTarget = resolveBuildTarget(project);
        updateProgress(project, "BUILDING", "Running electron-builder for " + buildTarget.cliValue + " target (this may take a few minutes)...");

        Map<String, String> envOverrides = buildEnvironmentOverridesFor(buildTarget);
        if (buildTarget == BuildTarget.WIN && !isWindows()) {
            ProcessResult wineboot = runProcessWithRetries(
                    project.getId(),
                    workspace,
                    "wineboot",
                    command("wineboot", "-u"),
                    envOverrides
            );
            if (wineboot.exitCode() != 0) {
                throw new IOException("wineboot failed with exit code " + wineboot.exitCode() + "\n" + wineboot.outputTail());
            }
        }

        // Build platform flags (shared between both execution paths)
        List<String> flags = new ArrayList<>();
        flags.add("--" + buildTarget.cliValue);
        if (buildTarget == BuildTarget.WIN && !isWindows()) {
            flags.add("--x64");
        }
        flags.add("--publish=never");
        if (electronBuilderDebug) {
            flags.add("--debug");
        }

        // On Linux/Docker: invoke via 'node <resolved-entry>' to bypass noexec tmpfs restriction.
        // Docker mounts the build workspace tmpfs with noexec by default; the OS blocks exec()
        // on noexec mounts but node reads JS with read(), which is not affected by noexec.
        // On Windows: .bin/electron-builder is a POSIX shell script — node cannot parse it,
        // so fall back to npx which handles Windows .cmd shims correctly.
        final String[] ebCommand;
        if (isWindows()) {
            List<String> winArgs = new ArrayList<>();
            winArgs.add("electron-builder");
            winArgs.addAll(flags);
            ebCommand = command("npx", winArgs.toArray(new String[0]));
        } else {
            String ebEntry = resolveNodeBinEntry(workspace, "electron-builder");
            log.debug("electron-builder entry point: {}", ebEntry);
            List<String> nodeArgs = new ArrayList<>();
            nodeArgs.add(ebEntry);
            nodeArgs.addAll(flags);
            ebCommand = command("node", nodeArgs.toArray(new String[0]));
        }

        ProcessResult result = runProcessWithRetries(
                project.getId(),
                workspace,
                "electron-builder",
                ebCommand,
                envOverrides
        );

        if (result.exitCode() != 0) {
            throw new IOException("electron-builder failed with exit code " + result.exitCode() + "\n" + result.outputTail());
        }

        log.info("electron-builder completed for '{}'", project.getProjectName());
    }

    private void uploadArtifact(ConversionProject project, Path workspace) throws IOException {
        BuildTarget buildTarget = resolveBuildTarget(project);
        updateProgress(project, "FINDING_ARTIFACT", "Looking for built installer artifact...");

        Path distDir = workspace.resolve("dist");
        if (!Files.isDirectory(distDir)) {
            throw new IOException("dist directory not found after build — electron-builder may have failed silently");
        }

        Path installerArtifact = findInstallerArtifact(distDir, buildTarget);
        if (installerArtifact == null) {
            throw new IOException("No installable artifact found in dist/ directory for target: " + buildTarget.cliValue);
        }

        log.info("Found artifact: {} ({}KB)", installerArtifact.getFileName(), Files.size(installerArtifact) / 1024);
        updateProgress(project, "UPLOADING_R2", "Uploading to cloud storage...");

        // Upload to R2
        String r2Key = String.format("builds/%s/%s/%s",
                project.getCreatedBy().replaceAll("[^a-zA-Z0-9@._-]", "_"),
                project.getId(),
                installerArtifact.getFileName().toString());

        String publicUrl = r2StorageService.uploadFile(installerArtifact, r2Key, "application/octet-stream");

        // Update project and increment build quota counter
        project.setStatus(ConversionStatus.READY);
        project.setBuildProgress("COMPLETE");
        project.setBuildArtifactPath(publicUrl);
        project.setR2Key(r2Key);
        project.setBuildError(null);
        project.setBuildCount(project.getBuildCount() != null ? project.getBuildCount() + 1 : 1);
        repository.save(project);

        // Record successful build
        long successDurationMs = project.getBuildStartedAt() != null
                ? Instant.now().toEpochMilli() - project.getBuildStartedAt().toEpochMilli() : 0;
        buildMetricsService.save(BuildRecord.builder()
                .projectId(project.getId())
                .projectName(project.getProjectName())
                .userEmail(project.getCreatedBy())
                .tier(project.getTier() != null ? project.getTier() : LicenseTier.TRIAL)
                .result("READY")
                .artifactUrl(publicUrl)
                .buildTarget(resolveBuildTarget(project).cliValue)
                .enabledModules(project.getEnabledModules())
                .completedAt(Instant.now())
                .durationMs(successDurationMs)
                .build());

        emitProgress(project.getId(), "COMPLETE", "Build complete! Download ready.");
        log.info("Build artifact uploaded to R2 for '{}': {}", project.getProjectName(), publicUrl);
    }

    // ─── Build Environment Helpers ──────────────────────

    private void validateBuildEnvironment(ConversionProject project) throws IOException, InterruptedException {
        updateProgress(project, "VALIDATING_ENV", "Checking build environment...");

        String nodeVersion = getToolVersion("node", "--version");
        if (nodeVersion == null) {
            throw new IOException(
                    "Node.js not found in PATH. Cannot build Electron app. " +
                    "Ensure Node.js is installed in the build environment. " +
                    "Effective PATH: " + buildEnvironment().getOrDefault("PATH", "(not set)"));
        }
        Integer nodeMajor = parseMajorVersion(nodeVersion);
        if (nodeMajor == null || nodeMajor < 20) {
            throw new IOException("Node.js 20+ required for this Electron template (found: " + nodeVersion + ")");
        }
        log.info("[env] node {}", nodeVersion);

        String npmVersion = getToolVersion(resolveExecutable("npm"), "--version");
        if (npmVersion == null) {
            throw new IOException("npm not found in PATH. Ensure npm is installed in the build environment.");
        }
        log.info("[env] npm  {}", npmVersion);

        BuildTarget buildTarget = resolveBuildTarget(project);
        if (buildTarget == BuildTarget.LINUX && "AppImage".equalsIgnoreCase(linuxTarget) && !isWindows()) {
            if (!isLibFuse2Available()) {
                throw new IOException(
                        "Missing 'libfuse2' in the build environment. " +
                        "electron-builder runs appimagetool (an AppImage) to produce AppImage installers, " +
                        "and appimagetool requires libfuse.so.2. " +
                        "Fix: install 'libfuse2' (Ubuntu/Debian) or change webtodesk.build.linux-target to 'deb'/'rpm'.");
            }
        }
        if (buildTarget == BuildTarget.WIN && !isWindows()) {
            String wineVersion = getToolVersion("wine", "--version");
            if (wineVersion == null) {
                throw new IOException(
                        "Building Windows installers on Linux requires Wine, but 'wine' was not found. " +
                        "Fix: install wine (and wine64/wine32) in the Docker image, or run Windows builds on a Windows host.");
            }
            log.info("[env] wine {}", wineVersion);
        }

        File outputDir = new File(buildOutputDir);
        outputDir.mkdirs();
        long freeSpaceMB = outputDir.getFreeSpace() / (1024L * 1024);
        if (freeSpaceMB < 512) {
            throw new IOException(
                    "Insufficient disk space: " + freeSpaceMB + " MB free in " + buildOutputDir +
                    ". Electron builds require at least 512 MB.");
        }
        log.info("[env] os='{}' target={} freeSpace={}MB outputDir={}",
                System.getProperty("os.name"), resolveBuildTarget().cliValue, freeSpaceMB, buildOutputDir);
    }

    private Map<String, String> buildEnvironment() {
        Map<String, String> env = new HashMap<>(System.getenv());

        if (!isWindows()) {
            String currentPath = env.getOrDefault("PATH", "");
            for (String nodePath : List.of("/usr/local/bin", "/usr/bin", "/bin")) {
                if (!currentPath.contains(nodePath)) {
                    currentPath = nodePath + File.pathSeparator + currentPath;
                }
            }
            env.put("PATH", currentPath);
        }

        env.computeIfAbsent("HOME", k -> System.getProperty("user.home", "/tmp"));

        String electronCache = env.getOrDefault("ELECTRON_CACHE",
                System.getProperty("java.io.tmpdir") + "/electron-cache");
        env.put("ELECTRON_CACHE", electronCache);
        new File(electronCache).mkdirs();

        env.put("CI", "true");
        env.put("ADBLOCK", "true");
        env.putIfAbsent("CSC_IDENTITY_AUTO_DISCOVERY", "false");

        env.putIfAbsent("npm_config_fetch_retries", "5");
        env.putIfAbsent("npm_config_fetch_retry_factor", "2");
        env.putIfAbsent("npm_config_fetch_retry_mintimeout", "20000");
        env.putIfAbsent("npm_config_fetch_retry_maxtimeout", "120000");

        String tmp = System.getProperty("java.io.tmpdir", "/tmp");
        env.putIfAbsent("XDG_CACHE_HOME", tmp + "/xdg-cache");
        env.putIfAbsent("ELECTRON_BUILDER_CACHE", tmp + "/electron-builder-cache");

        if (electronBuilderDebug) {
            env.put("ELECTRON_BUILDER_LOG_LEVEL", "debug");
            env.putIfAbsent("DEBUG", "electron-builder*");
        }
        return env;
    }

    private String getToolVersion(String tool, String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(tool);
            cmd.addAll(Arrays.asList(args));
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            pb.environment().putAll(buildEnvironment());
            Process p = pb.start();
            String out;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                out = reader.lines().collect(Collectors.joining(" ")).trim();
            }
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); }
            return out.isBlank() ? null : out;
        } catch (Exception e) {
            log.debug("Tool probe '{}' unavailable: {}", tool, e.getMessage());
            return null;
        }
    }

    // ─── Process Execution ───────────────────────────────

    /**
     * Runs a process and streams stdout/stderr lines to the SSE emitter in real-time.
     * Returns the exit code.
     */
    private record ProcessResult(int exitCode, String outputTail) {}

    private ProcessResult runProcess(String projectId, Path workingDir, String label, String[] command, Map<String, String> envOverrides) throws IOException, InterruptedException {
        log.info("Running ({}): {} in {}", label, String.join(" ", command), workingDir);

        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true); // merge stderr into stdout

        // Augment environment with correct PATH, HOME, ELECTRON_CACHE, CI flag
        // NOTE: Do NOT clear() the environment — Alpine's glib/library loader
        // relies on inherited env vars that get wiped by clear().
        // buildEnvironment() already starts from System.getenv() and overlays build-specific vars.
        pb.environment().putAll(buildEnvironment());
        if (envOverrides != null && !envOverrides.isEmpty()) {
            pb.environment().putAll(envOverrides);
        }

        Process process = pb.start();

        int maxTailLines = 120;
        ArrayDeque<String> tail = new ArrayDeque<>(maxTailLines + 1);
        Path logFile = workingDir.resolve("build.log");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int lineCount = 0;
            try (BufferedWriter logWriter = Files.newBufferedWriter(logFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                while ((line = reader.readLine()) != null) {
                    logWriter.write(line);
                    logWriter.newLine();

                    if (tail.size() >= maxTailLines) {
                        tail.removeFirst();
                    }
                    tail.addLast(line);

                    lineCount++;
                    boolean isErrorLine = isSignificantLogLine(line);
                    if (lineCount % 5 == 0 || isErrorLine) {
                        String truncated = line.length() > 120 ? line.substring(0, 120) + "..." : line;
                        emitProgress(projectId, isErrorLine ? "BUILD_ERROR" : "BUILD_LOG", truncated);
                    }
                }
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Build process timed out after 30 minutes: " + String.join(" ", command));
        }

        int exitCode = process.exitValue();
        log.info("Process exited with code {} for {}", exitCode, String.join(" ", command));
        String tailText = truncateForStorage(String.join("\n", tail), 20_000);
        if (exitCode != 0) {
            log.error("Process failed (exit {}). Last output:\n{}", exitCode, tailText);
        }
        return new ProcessResult(exitCode, tailText.isBlank() ? "(no output captured)" : tailText);
    }

    private ProcessResult runProcessWithRetries(
            String projectId,
            Path workingDir,
            String label,
            String[] command,
            Map<String, String> envOverrides
    ) throws IOException, InterruptedException {
        int attempts = Math.max(1, maxRetryAttempts);
        ProcessResult last = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                long delay = computeRetryDelayMs(attempt);
                emitProgress(projectId, "RETRYING", label + " retry " + attempt + "/" + attempts + " in " + delay + "ms");
                Thread.sleep(delay);
            }

            last = runProcess(projectId, workingDir, label, command, envOverrides);
            if (last.exitCode() == 0) {
                return last;
            }
            if (!isRetryableFailure(label, last.outputTail()) || attempt == attempts) {
                return last;
            }
        }

        return last == null ? new ProcessResult(1, "No output") : last;
    }

    private long computeRetryDelayMs(int attempt) {
        long base = Math.max(250L, retryBackoffMs);
        long pow = 1L << Math.min(10, Math.max(0, attempt - 2));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, retryJitterMs));
        long delay = base * pow + jitter;
        return Math.min(delay, 120_000L);
    }

    private boolean isRetryableFailure(String label, String output) {
        if (output == null) return true;
        String o = output.toLowerCase(Locale.ROOT);

        if (o.contains("etimedout")
                || o.contains("econnreset")
                || o.contains("eai_again")
                || o.contains("socket hang up")
                || o.contains("tls handshake timeout")
                || o.contains("network")
                || o.contains("timed out")
                || o.contains("response code 429")
                || o.contains("response code 502")
                || o.contains("response code 503")
                || o.contains("response code 504")
                || o.contains("http 429")
                || o.contains("http 502")
                || o.contains("http 503")
                || o.contains("http 504")) {
            return true;
        }

        if (label != null && label.toLowerCase(Locale.ROOT).contains("wineboot")) {
            return o.contains("err") || o.contains("failed") || o.contains("busy");
        }

        return false;
    }

    private static boolean isSignificantLogLine(String line) {
        if (line == null) return false;
        String l = line.toLowerCase(Locale.ROOT);
        return l.contains("error") || l.contains(" err ") || l.contains("err!") ||
               l.contains("failed") || l.contains("cannot") || l.contains("exception") ||
               l.contains("warn") || l.startsWith(">");
    }

    private String[] command(String executable, String... args) {
        List<String> command = new ArrayList<>();
        command.add(resolveExecutable(executable));
        command.addAll(Arrays.asList(args));
        return command.toArray(new String[0]);
    }

    private String resolveExecutable(String executable) {
        return isWindows() ? executable + ".cmd" : executable;
    }

    /**
     * Returns the absolute path to the JS entry-point behind a .bin/ symlink.
     * Using the resolved path with 'node' bypasses noexec tmpfs restrictions:
     * node reads JS files (read syscall) rather than exec()-ing them.
     */
    private String resolveNodeBinEntry(Path workspace, String binName) throws IOException {
        Path binLink = workspace.resolve("node_modules").resolve(".bin").resolve(binName);
        if (!Files.exists(binLink, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(binName + " not found in node_modules/.bin/ — run npm install first");
        }
        if (Files.isSymbolicLink(binLink)) {
            return binLink.toRealPath().toString();
        }
        return binLink.toAbsolutePath().toString();
    }

    private BuildTarget resolveBuildTarget() {
        return resolveBuildTarget(null);
    }

    private BuildTarget resolveBuildTarget(ConversionProject project) {
        // 1. Per-project preference (set by user in wizard)
        if (project != null && project.getTargetPlatform() != null && !project.getTargetPlatform().isBlank()) {
            BuildTarget projectTarget = BuildTarget.fromAlias(project.getTargetPlatform().trim().toLowerCase(Locale.ROOT));
            if (projectTarget != null) return projectTarget;
        }
        // 2. Env-var / application.yml override
        String requested = targetPlatform == null ? "" : targetPlatform.trim().toLowerCase(Locale.ROOT);
        if (!requested.isBlank() && !"auto".equals(requested)) {
            BuildTarget explicitTarget = BuildTarget.fromAlias(requested);
            if (explicitTarget != null) {
                return explicitTarget;
            }
            log.warn("Unknown build target platform '{}', falling back to auto detection", targetPlatform);
        }
        // 3. Auto-detect from server OS (Linux in Docker → LINUX)
        return isWindows() ? BuildTarget.WIN : BuildTarget.LINUX;
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name", "");
        return osName.toLowerCase(Locale.ROOT).contains("win");
    }

    private Path findInstallerArtifact(Path distDir, BuildTarget buildTarget) throws IOException {
        for (String extension : buildTarget.preferredExtensions) {
            Path artifact = findArtifactByExtension(distDir, extension, true);
            if (artifact != null) {
                return artifact;
            }
            artifact = findArtifactByExtension(distDir, extension, false);
            if (artifact != null) {
                return artifact;
            }
        }

        for (String extension : BuildTarget.GENERIC_INSTALLER_EXTENSIONS) {
            Path artifact = findArtifactByExtension(distDir, extension, true);
            if (artifact != null) {
                return artifact;
            }
        }

        for (String extension : BuildTarget.GENERIC_INSTALLER_EXTENSIONS) {
            Path artifact = findArtifactByExtension(distDir, extension, false);
            if (artifact != null) {
                return artifact;
            }
        }

        return null;
    }

    private Path findArtifactByExtension(Path distDir, String extension, boolean preferInstallerNames) throws IOException {
        try (Stream<Path> walk = Files.walk(distDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!name.endsWith(extension)) {
                            return false;
                        }
                        if (name.endsWith(".blockmap") || name.endsWith(".yml") || name.endsWith(".yaml")) {
                            return false;
                        }
                        if (!preferInstallerNames) {
                            return true;
                        }
                        return name.contains("setup") || name.contains("install") || name.contains("installer");
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    // ─── Workspace Cleanup ───────────────────────────────

    private void cleanupWorkspace(Path workspace) {
        try {
            if (Files.exists(workspace)) {
                // Walk tree in reverse order to delete files first, then directories
                try (Stream<Path> walk = Files.walk(workspace)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    log.warn("Failed to delete: {}", p);
                                }
                            });
                }
                log.info("Cleaned up workspace: {}", workspace);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup workspace {}: {}", workspace, e.getMessage());
        }
    }

    // ─── Progress helpers ────────────────────────────────

    private void updateProgress(ConversionProject project, String progress, String message) {
        project.setBuildProgress(progress);
        repository.save(project);
        emitProgress(project.getId(), progress, message);
        log.info("Build progress for '{}': {} — {}", project.getProjectName(), progress, message);
    }

    private void failBuild(ConversionProject project, String error) {
        log.error("Build failed for '{}': {}", project.getProjectName(), error);
        project.setStatus(ConversionStatus.FAILED);
        project.setBuildProgress("FAILED");
        project.setBuildError(error);
        project.setBuildArtifactPath(null);
        repository.save(project);

        // Record failed build
        long failDurationMs = project.getBuildStartedAt() != null
                ? Instant.now().toEpochMilli() - project.getBuildStartedAt().toEpochMilli() : 0;
        buildMetricsService.save(BuildRecord.builder()
                .projectId(project.getId())
                .projectName(project.getProjectName())
                .userEmail(project.getCreatedBy())
                .tier(project.getTier() != null ? project.getTier() : LicenseTier.TRIAL)
                .result("FAILED")
                .buildError(error)
                .buildTarget(resolveBuildTarget(project).cliValue)
                .enabledModules(project.getEnabledModules())
                .completedAt(Instant.now())
                .durationMs(failDurationMs)
                .build());

        emitProgress(project.getId(), "FAILED", error);
    }

    private void emitProgress(String projectId, String progress, String message) {
        try {
            List<SseEmitter> projectEmitters = emitters.get(projectId);
            if (projectEmitters == null || projectEmitters.isEmpty()) return;

            // Snapshot to avoid ConcurrentModificationException if a client connects/drops mid-send
            List<SseEmitter> snapshot = List.copyOf(projectEmitters);
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : snapshot) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(Map.of("progress", progress, "message", message)));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            projectEmitters.removeAll(dead);
        } catch (Exception ignored) {
            log.debug("emitProgress swallowed exception for project {}: {}", projectId, ignored.getMessage());
        }
    }

    private void removeEmitter(String projectId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(projectId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(projectId);
        }
    }

    // ─── Electron File Generators ────────────────────────

    private Map<String, String> generateFiles(ConversionProject project) {
        LicenseTier tier = project.getTier() != null ? project.getTier() : LicenseTier.TRIAL;
        List<String> resolved = moduleRegistry.resolveEnabledModules(project.getEnabledModules(), tier, developmentBuild);

        StringBuilder requires = new StringBuilder();
        StringBuilder setups = new StringBuilder();
        for (String key : resolved) {
            String varName = key.replace('-', '_');
            requires.append("const ").append(varName)
                    .append(" = require('./modules/").append(key).append("');\n");
            setups.append("  ").append(varName).append(".setup(mainWindow, config);\n");
        }

        String modulesJson = resolved.isEmpty() ? "[]"
                : "[\"" + String.join("\",\"", resolved) + "\"]"; 

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("projectName",   project.getProjectName());
        ctx.put("npmPackageName", sanitizeNpmPackageName(project.getProjectName()));
        ctx.put("appId", buildAppId(project.getProjectName()));
        ctx.put("currentVersion", project.getCurrentVersion() != null ? project.getCurrentVersion() : "1.0.0");
        ctx.put("appTitle",       project.getAppTitle());
        ctx.put("websiteUrl",     project.getWebsiteUrl());
        ctx.put("iconFile",       project.getIconFile() != null ? project.getIconFile() : "icon.ico");
        ctx.put("modulesJson",    modulesJson);
        ctx.put("hasModules",      !resolved.isEmpty());
        ctx.put("hasScreenProtect", resolved.contains("screen-protect"));
        ctx.put("moduleRequires",  requires.toString());
        ctx.put("moduleSetups",    setups.toString());

        // Build-target context — drives platform-specific section in package.mustache
        BuildTarget target = resolveBuildTarget(project);
        ctx.put("isWin",   target == BuildTarget.WIN);
        ctx.put("isLinux", target == BuildTarget.LINUX);
        ctx.put("isMac",   target == BuildTarget.MAC);
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

        log.debug("Generated {} files ({} modules) for project '{}'",
                files.size(), resolved.size(), project.getProjectName());
        return files;
    }

    private static Integer parseMajorVersion(String version) {
        if (version == null) return null;
        String trimmed = version.trim();
        if (trimmed.startsWith("v")) trimmed = trimmed.substring(1);
        int dot = trimmed.indexOf('.');
        String major = dot >= 0 ? trimmed.substring(0, dot) : trimmed;
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String sanitizeNpmPackageName(String projectName) {
        String base = projectName == null ? "" : projectName.trim().toLowerCase(Locale.ROOT);
        base = base.replaceAll("\\s+", "-");
        base = base.replaceAll("[^a-z0-9._-]", "-");
        base = base.replaceAll("-{2,}", "-");
        base = base.replaceAll("^[._-]+", "");
        base = base.replaceAll("[._-]+$", "");
        if (base.isBlank()) return "webtodesk-app";
        if (base.length() > 214) return base.substring(0, 214);
        return base;
    }

    private static String buildAppId(String projectName) {
        String base = projectName == null ? "" : projectName.trim().toLowerCase(Locale.ROOT);
        base = base.replaceAll("[^a-z0-9]+", ".");
        base = base.replaceAll("\\.{2,}", ".");
        base = base.replaceAll("^\\.+", "");
        base = base.replaceAll("\\.+$", "");
        if (base.isBlank()) return "com.webtodesk.app";
        return "com.webtodesk." + base;
    }

    private static String truncateForStorage(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(text.length() - maxChars);
    }

    private Map<String, String> buildEnvironmentOverridesFor(BuildTarget target) {
        if (target == null) return Map.of();
        if (target != BuildTarget.WIN || isWindows()) return Map.of();

        Map<String, String> overrides = new HashMap<>();
        overrides.put("WINEDEBUG", "-all");

        String winePrefix = "/tmp/wine-prefix";
        overrides.putIfAbsent("WINEPREFIX", winePrefix);
        overrides.putIfAbsent("WINEARCH", "win64");

        try {
            Files.createDirectories(Paths.get(winePrefix));
        } catch (Exception ignored) {}
        return overrides;
    }

    private boolean isLibFuse2Available() {
        try {
            if (Files.exists(Paths.get("/lib/x86_64-linux-gnu/libfuse.so.2"))) return true;
            if (Files.exists(Paths.get("/usr/lib/x86_64-linux-gnu/libfuse.so.2"))) return true;
            if (Files.exists(Paths.get("/lib/libfuse.so.2"))) return true;
            if (Files.exists(Paths.get("/usr/lib/libfuse.so.2"))) return true;
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private enum BuildTarget {
        WIN("win", List.of(".exe", ".msi")),
        LINUX("linux", List.of(".appimage", ".deb", ".rpm")),
        MAC("mac", List.of(".dmg", ".zip"));

        private static final List<String> GENERIC_INSTALLER_EXTENSIONS =
                List.of(".exe", ".msi", ".appimage", ".deb", ".rpm", ".dmg", ".zip");

        private final String cliValue;
        private final List<String> preferredExtensions;

        BuildTarget(String cliValue, List<String> preferredExtensions) {
            this.cliValue = cliValue;
            this.preferredExtensions = preferredExtensions;
        }

        private static BuildTarget fromAlias(String value) {
            return switch (value) {
                case "win", "windows", "win32" -> WIN;
                case "linux", "docker" -> LINUX;
                case "mac", "darwin", "macos", "osx" -> MAC;
                default -> null;
            };
        }
    }
}
