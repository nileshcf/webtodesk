package com.example.conversion_service.service;

import com.example.conversion_service.entity.ConversionProject;
import com.example.conversion_service.entity.ConversionProject.ConversionStatus;
import com.example.conversion_service.exception.ProjectNotFoundException;
import com.example.conversion_service.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildService {

    private final ConversionRepository repository;
    private final R2StorageService r2StorageService;

    @Value("${webtodesk.build.output-dir:${java.io.tmpdir}/webtodesk-builds}")
    private String buildOutputDir;

    @Value("${webtodesk.build.target-platform:auto}")
    private String targetPlatform;

    // SSE emitters per projectId — multiple clients can subscribe
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // ─── Public API ──────────────────────────────────────

    /**
     * Triggers a LOCAL build for the Electron app.
     * 1. Clone the webtodesk repo into a temp workspace
     * 2. Write generated Electron config files
     * 3. Run npm install
     * 4. Run electron-builder --win
     * 5. Upload the .exe artifact to R2
     * 6. Stream progress via SSE throughout
     */
    @Async("buildExecutor")
    public void triggerBuild(String projectId) {
        ConversionProject project = repository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        project.setStatus(ConversionStatus.BUILDING);
        project.setBuildProgress("PREPARING");
        project.setBuildStartedAt(Instant.now());
        project.setBuildError(null);
        project.setBuildArtifactPath(null);
        project.setR2Key(null);
        repository.save(project);
        emitProgress(projectId, "PREPARING", "Preparing build workspace...");

        Path workspace = null;

        try {
            // 1. Create workspace
            workspace = createWorkspace(projectId);

            // 2. Write generated Electron files into workspace
            writeElectronFiles(project, workspace);

            // 3. npm install
            runNpmInstall(project, workspace);

            // 4. electron-builder for the selected target platform
            runElectronBuilder(project, workspace);

            // 5. Find the built .exe and upload to R2
            uploadArtifact(project, workspace);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failBuild(project, "Build interrupted");
        } catch (Exception e) {
            log.error("Build failed for '{}': {}", project.getProjectName(), e.getMessage(), e);
            failBuild(project, e.getMessage());
        } finally {
            // 6. Cleanup workspace
            if (workspace != null) {
                cleanupWorkspace(workspace);
            }
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

        int exitCode = runProcess(project.getId(), workspace, command("npm", "install", "--no-audit", "--no-fund"));

        if (exitCode != 0) {
            throw new IOException("npm install failed with exit code " + exitCode);
        }

        log.info("npm install completed for '{}'", project.getProjectName());
    }

    private void runElectronBuilder(ConversionProject project, Path workspace) throws IOException, InterruptedException {
        BuildTarget buildTarget = resolveBuildTarget();
        updateProgress(project, "BUILDING", "Running electron-builder for " + buildTarget.cliValue + " target (this may take a few minutes)...");

        int exitCode = runProcess(project.getId(), workspace,
                command("npx", "electron-builder", "--" + buildTarget.cliValue, "--publish=never"));

        if (exitCode != 0) {
            throw new IOException("electron-builder failed with exit code " + exitCode);
        }

        log.info("electron-builder completed for '{}'", project.getProjectName());
    }

    private void uploadArtifact(ConversionProject project, Path workspace) throws IOException {
        BuildTarget buildTarget = resolveBuildTarget();
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

        // Update project
        project.setStatus(ConversionStatus.READY);
        project.setBuildProgress("COMPLETE");
        project.setBuildArtifactPath(publicUrl);
        project.setR2Key(r2Key);
        project.setBuildError(null);
        repository.save(project);

        emitProgress(project.getId(), "COMPLETE", "Build complete! Download ready.");
        log.info("Build artifact uploaded to R2 for '{}': {}", project.getProjectName(), publicUrl);
    }

    // ─── Process Execution ───────────────────────────────

    /**
     * Runs a process and streams stdout/stderr lines to the SSE emitter in real-time.
     * Returns the exit code.
     */
    private int runProcess(String projectId, Path workingDir, String[] command) throws IOException, InterruptedException {
        log.info("Running: {} in {}", String.join(" ", command), workingDir);

        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true); // merge stderr into stdout

        // Inherit PATH so node/npm/git are found
        pb.environment().putAll(System.getenv());

        Process process = pb.start();

        // Stream output lines to SSE in real-time
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                log.debug("[build:{}] {}", projectId, line);
                lineCount++;
                // Send every 5th line to SSE to avoid flooding the client
                if (lineCount % 5 == 0) {
                    String truncated = line.length() > 120 ? line.substring(0, 120) + "..." : line;
                    emitProgress(projectId, "BUILD_LOG", truncated);
                }
            }
        }

        int exitCode = process.waitFor();
        log.info("Process exited with code {} for {}", exitCode, String.join(" ", command));
        return exitCode;
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

    private BuildTarget resolveBuildTarget() {
        String requested = targetPlatform == null ? "" : targetPlatform.trim().toLowerCase(Locale.ROOT);
        if (!requested.isBlank() && !"auto".equals(requested)) {
            BuildTarget explicitTarget = BuildTarget.fromAlias(requested);
            if (explicitTarget != null) {
                return explicitTarget;
            }
            log.warn("Unknown build target platform '{}', falling back to auto detection", targetPlatform);
        }
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
        emitProgress(project.getId(), "FAILED", error);
    }

    private void emitProgress(String projectId, String progress, String message) {
        List<SseEmitter> projectEmitters = emitters.get(projectId);
        if (projectEmitters == null || projectEmitters.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : projectEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(Map.of("progress", progress, "message", message)));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        projectEmitters.removeAll(dead);
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
        Map<String, String> files = new LinkedHashMap<>();
        files.put("config.js", generateConfigJs(project));
        files.put("main.js", generateMainJs());
        files.put("preload.js", generatePreloadJs());
        files.put("package.json", generatePackageJson(project));
        return files;
    }

    private String generateConfigJs(ConversionProject project) {
        return """
                // Generated by WebToDesk Conversion Service
                module.exports = {
                  projectName: '%s',
                  currentVersion: '%s',
                  appTitle: '%s',
                  websiteUrl: '%s',
                  iconFile: '%s'
                };
                """.formatted(
                project.getProjectName(),
                project.getCurrentVersion(),
                project.getAppTitle(),
                project.getWebsiteUrl(),
                project.getIconFile()
        );
    }

    private String generateMainJs() {
        return """
                const { app, BrowserWindow, globalShortcut, ipcMain, shell } = require('electron');
                const path = require('path');
                const { appTitle, websiteUrl, iconFile } = require('./config');

                async function createWindow() {
                  const getIconPath = () =>
                    path.join(__dirname, 'build',
                      process.platform === 'darwin' ? 'icon.icns' :
                      process.platform === 'win32'  ? iconFile : 'icon.png');

                  const mainWindow = new BrowserWindow({
                    width: 1200,
                    height: 800,
                    title: appTitle,
                    icon: getIconPath(),
                    webPreferences: {
                      nodeIntegration: false,
                      contextIsolation: true,
                      enableRemoteModule: false,
                      preload: path.join(__dirname, 'preload.js')
                    }
                  });

                  mainWindow.loadURL(websiteUrl).catch(err => {
                    console.error('Failed to load URL:', err.message);
                  });

                  mainWindow.webContents.on('page-title-updated', event => {
                    event.preventDefault();
                    mainWindow.setTitle(appTitle);
                  });

                  if (process.platform === 'darwin' || process.platform === 'win32') {
                    mainWindow.setContentProtection(true);
                  }

                  mainWindow.webContents.on('devtools-opened', () => {
                    mainWindow.webContents.closeDevTools();
                  });

                  mainWindow.setMenuBarVisibility(false);

                  const registerShortcuts = () => {
                    const shortcuts = [
                      'PrintScreen', 'Alt+PrintScreen', 'Super+Shift+S',
                      'Super+PrintScreen', 'Command+Shift+3',
                      'Command+Shift+4', 'Command+Shift+5'
                    ];
                    shortcuts.forEach(shortcut => {
                      globalShortcut.register(shortcut, () => {
                        if (mainWindow?.isDestroyed?.()) return;
                        mainWindow.webContents.send('trigger-protection');
                      });
                    });
                  };

                  registerShortcuts();
                  mainWindow.on('focus', registerShortcuts);
                  mainWindow.on('blur', () => globalShortcut.unregisterAll());
                }

                app.whenReady().then(createWindow);

                app.on('will-quit', () => globalShortcut.unregisterAll());

                app.on('window-all-closed', () => {
                  if (process.platform !== 'darwin') app.quit();
                });

                app.on('activate', () => {
                  if (!BrowserWindow.getAllWindows().length) createWindow();
                });

                ipcMain.on('open-external', (event, url) => shell.openExternal(url));
                """;
    }

    private String generatePreloadJs() {
        return """
                const { contextBridge, ipcRenderer } = require('electron');

                contextBridge.exposeInMainWorld('electronAPI', {
                  openExternal: url => ipcRenderer.send('open-external', url)
                });

                (function initializeProtectionUI() {
                  const blackout = document.createElement('div');
                  blackout.style.cssText = `
                    position: fixed; top: 0; left: 0; width: 100%; height: 100%;
                    background: linear-gradient(to bottom, #000000, #1a1a1a);
                    z-index: 10001; display: none; pointer-events: none;
                    opacity: 0; transition: opacity 0.3s ease-in-out;
                  `;
                  document.body.appendChild(blackout);

                  const timerDisplay = document.createElement('div');
                  timerDisplay.style.cssText = `
                    position: absolute; top: 60%; left: 50%;
                    transform: translate(-50%, -50%); color: #ffffff;
                    font-size: 24px; z-index: 10003; font-family: Arial, sans-serif;
                    opacity: 0; transition: opacity 0.3s ease-in-out;
                  `;
                  blackout.appendChild(timerDisplay);

                  const snackbar = document.createElement('div');
                  snackbar.textContent = 'Screenshots and recordings are not allowed.';
                  snackbar.style.cssText = `
                    position: fixed; top: 50%; left: 50%;
                    transform: translate(-50%, -50%); background-color: #d32f2f;
                    color: white; padding: 20px 40px; border-radius: 12px;
                    z-index: 10002; opacity: 0; transition: opacity 0.3s ease-in-out, transform 0.3s ease-in-out;
                    display: none; font-size: 20px; text-align: center;
                    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5); font-family: Arial, sans-serif;
                  `;
                  document.body.appendChild(snackbar);

                  let isProtectionActive = false;
                  let protectionTimer = null;

                  const flashBorder = () => {
                    document.body.style.border = '8px solid red';
                    let flashCount = 0;
                    const flashInterval = setInterval(() => {
                      document.body.style.borderColor = flashCount % 2 ? 'transparent' : 'red';
                      if (++flashCount >= 8) {
                        clearInterval(flashInterval);
                        document.body.style.border = 'none';
                      }
                    }, 150);
                  };

                  const triggerProtection = () => {
                    if (isProtectionActive) return;
                    isProtectionActive = true;
                    blackout.style.display = 'block';
                    blackout.style.opacity = '1';
                    snackbar.style.display = 'block';
                    snackbar.style.opacity = '1';
                    snackbar.style.transform = 'translate(-50%, -50%) scale(1.05)';
                    setTimeout(() => { snackbar.style.transform = 'translate(-50%, -50%) scale(1)'; }, 300);
                    timerDisplay.style.opacity = '1';
                    flashBorder();
                    let timeLeft = 10;
                    timerDisplay.textContent = `Resuming in ${timeLeft}...`;
                    protectionTimer = setInterval(() => {
                      timerDisplay.textContent = `Resuming in ${--timeLeft}...`;
                      if (timeLeft <= 0) {
                        clearInterval(protectionTimer);
                        snackbar.style.opacity = blackout.style.opacity = timerDisplay.style.opacity = '0';
                        setTimeout(() => {
                          snackbar.style.display = blackout.style.display = 'none';
                          isProtectionActive = false;
                        }, 300);
                      }
                    }, 1000);
                  };

                  ipcRenderer.on('trigger-protection', triggerProtection);
                })();
                """;
    }

    private String generatePackageJson(ConversionProject project) {
        return """
                {
                  "name": "%s",
                  "version": "%s",
                  "description": "Desktop app for %s",
                  "main": "main.js",
                  "scripts": {
                    "start": "electron .",
                    "dist": "electron-builder --publish=never"
                  },
                  "devDependencies": {
                    "electron": "^38.2.2",
                    "electron-builder": "^26.0.12"
                  },
                  "build": {
                    "appId": "com.%s.app",
                    "productName": "%s",
                    "directories": { "output": "dist" },
                    "files": ["main.js", "preload.js", "config.js"],
                    "win": { "target": "nsis" },
                    "mac": { "category": "public.app-category.utilities" },
                    "linux": { "target": "AppImage" }
                  }
                }
                """.formatted(
                project.getProjectName(),
                project.getCurrentVersion(),
                project.getAppTitle(),
                project.getProjectName(),
                project.getAppTitle()
        );
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
