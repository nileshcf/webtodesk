# Changelog

All notable changes to this project are documented in this file.

---

## [1.3.0] - 2026-03-28 — Unified Dockerization & Electron-in-Docker Fixes

### Overview

Full containerization of the WebToDesk platform with optimized multi-stage builds and Nginx routing. Resolved critical execution failures for `electron-builder` on Alpine Linux, enabling a functional local build pipeline inside the containerized environment.

### Added

#### Unified Dockerization

- **`Dockerfile`**: Implemented a comprehensive multi-stage Dockerfile that builds all Java microservices (Discovery, User, Conversion, API Gateway) and the React frontend simultaneously.
- **`nginx.conf`**: Configured Nginx as a reverse proxy to route `/user/**` and `/conversion/**` requests to their respective backend services, ensuring seamless frontend-to-backend communication.
- **`.dockerignore`**: Added to maintain small image sizes (down from ~2GB to ~450MB) and exclude local `target/` and `node_modules/` folders.

#### OS Compatibility (Alpine/glibc)

- **`Dockerfile`**: Added `glib` and `gcompat` packages to the final Alpine image. This provides the necessary glibc compatibility shims for `electron-builder`'s `app-builder-bin`, which is a glibc-linked Go binary.

### Fixed

#### Electron Build Pipeline

- **`ERR_ELECTRON_BUILDER_CANNOT_EXECUTE`**: Fixed the root cause where the `app-builder` binary was incompatible with Alpine's musl libc.
- **`BuildService.java` Environment Isolation**: Removed `pb.environment().clear()` in `runProcess()`. This preventing the inadvertent wiping of critical system environment variables (like library loader paths) required for the `gcompat` shims to function.
- **`start-all.ps1`**:
  - Added explicit environment variable `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"` to override an invalid system configuration pointing to JDK 11.
  - Fixed pathing for the Maven wrapper inside all subdirectories (e.g. `discovery-service`, `user-service`) by calling `..\mvnw.cmd` instead of `mvnw.cmd`.

#### Docker Build Stability

- **`Dockerfile` (Multi-stage fixes)**: Added missing `COPY` statements for `conversion-service/pom.xml` and `discovery-service/pom.xml` to all backend build stages to resolve Maven multi-module dependency errors ("Child module does not exist").
- **`entrypoint.sh`**: Robust startup script that waits for background services and ensures environment-specific Nginx templates are applied.

### Verified

- **End-to-End Build**: Confirmed successful `electron-builder --linux` execution inside the container.
- **R2 Storage Integration**: Verified that packaged `.AppImage` files (e.g., `Final Test App-1.0.0.AppImage`) are automatically uploaded to Cloudflare R2 from within the Docker network.
- **Routing**: Verified that the API Gateway correctly handles JWT authentication and proxies to the Conversion and User services.

---

## [1.2.0] - 2026-03-27 — Conversion Service: Local Build Pipeline & SSE Streaming

### Overview

Completion of the Week 1 conversion service build pipeline. Replaced the proposed GitHub Actions workflow with a robust **Local Node.js/Electron Build Pipeline** using `ProcessBuilder`. Added real-time build log streaming via **Server-Sent Events (SSE)** and finalized the **Cloudflare R2** deployment strategy.

### Added

#### Local Build Orchestration

- **`BuildService.java`**: Implemented generic `ProcessBuilder` logic to clone a base Electron template repository, write generated `config.js`, `main.js`, and `package.json` configurations into the local workspace, and execute `npm install` followed by `npx electron-builder --win`.
- **`Workspace Cleanup`**: Added `FileSystemUtils.deleteRecursively()` to ensure temp build directories are purged after build completion or failure.

#### Real-Time Streaming (SSE)

- **`BuildService.java`**: Streams `stdout` and `stderr` directly from the local build process to the client via `SseEmitter`.
- **`ConversionController.java`**: Added `GET /conversions/{id}/build/stream` endpoint returning `text/event-stream`.
- **Frontend Integration**: Switched the React dashboard from a 10s polling interval to an active SSE stream using `@microsoft/fetch-event-source` to forward Bearer tokens properly. Added a pulsing progress bar mapping string statuses (Dispatching → Queued → Building → Uploading → Ready).

#### Cloudflare R2 Upload

- **`BuildService`**: After a successful local build, locates the generated `.exe` file in the `dist` folder and uploads it seamlessly to Cloudflare R2 using the `R2StorageService`. Emits the public R2 download URL back to the frontend.

### Removed

- Cancelled the `electron-build.yml` GitHub Actions setup as local builds orchestrate the process natively and faster.
- Removed webhook callback endpoints that were intended for GitHub Actions since the backend tracks local build statuses intrinsically.
- Removed the separate "Preview generated files" button from the frontend dashboard.

---

## [1.1.0] - 2026-03-27 — Conversion Service: Week 1 Foundation Hardening

### Overview

Week 1 of the phased conversion-service overhaul. This release hardens the foundation layer with
structured exception handling, request validation, a health check endpoint, externalized configuration,
dedicated MongoDB auditing config, and a comprehensive test suite. **Zero business logic changes** —
all existing endpoints and frontend integration remain fully backward-compatible.

### Added

#### Exception Handling

- **`exception/ProjectNotFoundException.java`**: Typed runtime exception for missing conversion projects, replacing raw `RuntimeException` throws throughout the service layer. Produces a consistent `"Conversion project not found: {id}"` message.
- **`exception/GlobalExceptionHandler.java`**: `@RestControllerAdvice` providing centralized error handling across all controllers:
  - `ProjectNotFoundException` → **404 Not Found** with structured JSON body
  - `MethodArgumentNotValidException` → **400 Bad Request** with per-field validation errors map
  - `MissingRequestHeaderException` → **400 Bad Request** (e.g., missing `X-User-Email` header)
  - `IllegalArgumentException` → **400 Bad Request** with descriptive message
  - Generic `Exception` → **500 Internal Server Error** with safe message and server-side stack trace logging
- **`dto/ErrorResponse.java`**: Immutable record for structured error responses with fields: `message`, `errors` (field-level map), `timestamp`, and `path`. Includes factory methods `ErrorResponse.of(...)` for consistent construction.

#### Health Check

- **`controller/HealthController.java`**: New `GET /conversions/health` endpoint returning `{"status":"UP","service":"conversion-service"}` for liveness probes and monitoring.

#### Configuration

- **`config/MongoConfig.java`**: Dedicated `@Configuration` class with `@EnableMongoAuditing`, extracted from `ConversionSecurityConfig` to follow single-responsibility principle.
- **`application-dev.yml`**: Local development profile with explicit `mongodb://localhost:27017/webtodesk` URI and Eureka URL.
- **`application-test.yml`**: Test profile with embedded-compatible MongoDB URI and disabled Eureka registration.

#### Request Validation

- **`dto/CreateConversionRequest`** — enriched with Bean Validation annotations:
  - `projectName`: `@NotBlank`, `@Size(max=64)`, `@Pattern` (letters, numbers, spaces, hyphens, underscores only)
  - `websiteUrl`: `@NotBlank`, `@Pattern` (must start with `https://`)
  - `appTitle`: `@NotBlank`, `@Size(max=128)`
- **`dto/UpdateConversionRequest`** — enriched with conditional validation:
  - `websiteUrl`: `@Pattern` (must start with `https://` when provided)
  - `appTitle`: `@Size(max=128)`
  - `currentVersion`: `@Pattern` (semantic versioning format `X.Y.Z` when provided)

#### Test Suite (28 tests — all passing)

- **`ConversionServiceTest.java`** — 18 unit tests covering:
  - Project creation with default values, custom icon, and name sanitization
  - Listing projects by user email (populated and empty results)
  - Get-by-ID success and not-found scenarios
  - Update of individual fields (websiteUrl, appTitle, iconFile, currentVersion, projectName)
  - Delete success and not-found scenarios
  - Electron project generation (file map verification, status transition to READY)
  - Generation for non-existent project (not-found exception)
- **`ConversionControllerTest.java`** — 10 unit tests covering:
  - Health endpoint response body and status code
  - Create delegation to service layer
  - List projects by user email
  - Get-by-ID success and `ProjectNotFoundException` propagation
  - Update with response verification
  - Delete with 204 status and not-found exception propagation
  - Generate with Electron file map verification and not-found exception propagation

#### Build Infrastructure

- **`conversion-service/mvnw`** and **`conversion-service/mvnw.cmd`**: Maven wrapper scripts added to enable standalone builds without requiring a system-wide Maven installation.
- **`conversion-service/.mvn/wrapper/maven-wrapper.properties`**: Maven wrapper configuration file.
- **Test dependencies** added to `conversion-service/pom.xml`:
  - `spring-boot-starter-test` (scope: test)
  - `spring-boot-starter-validation` (runtime)
  - `assertj-core` 3.26.3 (scope: test)

### Changed

- **`controller/ConversionController.java`**:
  - All `@PathVariable` annotations now use explicit name parameter (`@PathVariable("id")`) to resolve `IllegalArgumentException` caused by missing `-parameters` compiler flag. This was the root cause of all path-variable endpoints returning 500 in production.
  - Added `@Valid` annotation to `UpdateConversionRequest` parameter on the `PUT /{id}` endpoint.
- **`service/ConversionService.java`**: All `RuntimeException` throws replaced with `ProjectNotFoundException` for type-safe exception handling.
- **`config/ConversionSecurityConfig.java`**: Removed `@EnableMongoAuditing` annotation (moved to dedicated `MongoConfig.java`).
- **`application.yml`**: MongoDB URI and Eureka URL externalized via environment variables with sensible defaults:
  - `MONGODB_URI` → defaults to cloud Atlas connection string
  - `EUREKA_URL` → defaults to `http://localhost:8761/eureka/`

### Fixed

- **Critical: `@PathVariable` resolution failure** — All endpoints using `@PathVariable String id` (`GET /{id}`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/generate`) returned **HTTP 500** with `IllegalArgumentException: Name for argument of type [java.lang.String] not specified`. Root cause: Java compiler does not preserve method parameter names by default without the `-parameters` flag. Fix: explicit `@PathVariable("id")` on all path variable parameters.

### Verified Endpoints (Live Testing)

| Endpoint                     | Method | Status | Description                              |
| ---------------------------- | ------ | ------ | ---------------------------------------- |
| `/conversions/health`        | GET    | 200    | Health check                             |
| `/conversions`               | POST   | 200    | Create project (with validation)         |
| `/conversions`               | GET    | 200    | List user projects                       |
| `/conversions/{id}`          | GET    | 200    | Get project by ID                        |
| `/conversions/{id}`          | PUT    | 200    | Update project (with validation)         |
| `/conversions/{id}`          | DELETE | 204    | Delete project                           |
| `/conversions/{id}/generate` | POST   | 200    | Generate Electron files                  |
| `/conversions/{id}`          | GET    | 404    | Not found (structured error)             |
| `/conversions`               | POST   | 400    | Validation error (structured errors map) |

---

## [Unreleased] - Troubleshooting and Startup Fixes

### Added

- **`discovery-service/Dockerfile`**: Created missing multi-stage Dockerfile to enable building and running the discovery service via Docker Compose.
- **`conversion-service/Dockerfile`**: Created missing multi-stage Dockerfile to enable building and running the conversion service via Docker Compose.
- **`.mvn/wrapper/maven-wrapper.properties`**: Re-added the missing properties file for the Maven wrapper to ensure `mvnw.cmd` can download and execute Maven dependencies directly on fresh setups.

### Fixed

- **`start-all.ps1`**:
  - Added explicit environment variable `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"` to override an invalid system configuration pointing to JDK 11. This fixed the instant crashes during startup.
  - Fixed pathing for the Maven wrapper inside all subdirectories (e.g. `discovery-service`, `user-service`) by calling `..\mvnw.cmd` instead of `mvnw.cmd`, matching the wrapper location in the project root.
- **`api-gateway/Dockerfile`**:
  - Added missing `COPY` statements for `conversion-service/pom.xml` and `discovery-service/pom.xml`. This prevents Maven multi-module dependency errors ("Child module does not exist") during the Docker build stage.
- **`user-service/Dockerfile`**:
  - Added missing `COPY` statements for `conversion-service/pom.xml` and `discovery-service/pom.xml` to resolve the same multi-module build crash.

### Other

- **`frontend/`**: Ran `npm install` to initialize all missing node modules and frontend dependencies.
