---
name: webtodesk-conversion-service
description: >
  Expert skill for the WebToDesk conversion-service — a Spring Boot 3.3 microservice
  (Java 17, MongoDB, Eureka) that converts website URLs into packaged Electron desktop apps
  and delivers them via Cloudflare R2 after building them locally via ProcessBuilder.
  Part of a multi-module Maven project with api-gateway, user-service, discovery-service, and
  a React/Vite frontend. Use this skill for: upgrading the conversion-service architecture,
  adding Electron modules, improving the template engine, wiring build pipelines, R2 storage,
  GitHub Actions integration, or any task touching conversion-service code.
  The agent follows a 4-week phased overhaul plan (§8) — each week is self-contained.
  **Week 1 (Foundation + R2 + Local Builds) is COMPLETE as of 2026-03-27.**
  Current week: Week 2.
  Activates for: webtodesk, conversion service, electron app builder, url to desktop, desktop
  wrapper, electron template, build pipeline, conversion project, R2, cloudflare, artifact storage.
stack: Java 17, Spring Boot 3.3.6, Spring Cloud 2023.0.4, MongoDB, Eureka, Lombok,
       AWS SDK S3 2.25.0, Cloudflare R2, Local ProcessBuilder (npm, npx),
       React 19/Vite 6, Electron 38+, electron-builder 26+, Docker Compose
principles: microservices, service-discovery, backwards-compatibility, incremental-delivery,
            zero-downtime, test-before-refactor, modular-templates, fail-safe-defaults
---

# WebToDesk Conversion Service — Overhaul Skill

> This skill gives an AI agent complete context on the **actual** codebase, a gap analysis against
> the target vision, and a safe 4-week phased plan to upgrade the conversion-service from its
> current MVP state to a production-grade Electron app factory — without breaking anything.
> Includes the full R2 cloud storage and Local Node.js / Electron build pipeline integration.

---

## 0. AGENT ACTIVATION PROTOCOL

On every invocation the agent MUST:

1. **Read this entire file** before writing a single line of code.
2. **Check the current week** of the overhaul (§8) — only work within that week's scope.
3. **Verify existing tests pass** before making changes: `mvn test -pl conversion-service`.
4. **Emit a TODO list** (§2 format) scoped to the current task.
5. **Execute sequentially**, compiling and testing after each file change.
6. **Never delete or modify** existing working endpoints without a migration path.
7. **Self-audit** against the quality gates in §9 before delivering.

> **Cardinal Rule:** Every commit must leave the service in a deployable state. If a feature
> spans multiple sessions, use feature flags or additive-only changes.

---

## 1. CURRENT STATE AUDIT (post-Week 1, as of 2026-03-27)

### 1.1 What Exists

| Layer | File | Status | Notes |
|-------|------|--------|-------|
| **Entity** | `ConversionProject.java` | **Hardened** | 14 fields incl. `githubRunId`, `r2Key`, `buildArtifactPath`, `buildError`. `ConversionStatus` enum (DRAFT/READY/BUILDING/FAILED). MongoDB `@Document` |
| **Repository** | `ConversionRepository.java` | MVP | `MongoRepository`, single custom query `findByCreatedByOrderByCreatedAtDesc` |
| **Service** | `ConversionService.java` | **Hardened** | CRUD + `generateElectronProject()`. Uses `ProjectNotFoundException`. |
| **Service** | `BuildService.java` | **Week 1+** | Local workspace build. Key methods: `triggerBuild` (async orchestrator), `validateBuildEnvironment` (pre-flight: node/npm probe + disk check), `buildEnvironment` (augmented PATH/HOME/ELECTRON_CACHE map), `getToolVersion` (safe 10s tool probe), `runProcess` (30-min timeout + last-20-line tail log on failure), `resolveBuildTarget` (auto/win/linux/mac via `BuildTarget` enum), `resolveExecutable` (`.cmd` on Windows, bare on Linux). Artifact discovery is extension-aware (.exe/.msi/.AppImage/.deb/.rpm/.dmg/.zip). |
| **Service** | `R2StorageService.java` | **Week 1** | Upload file/stream, delete, exists, getPublicUrl — uses AWS S3 SDK against R2 |
| **Controller** | `ConversionController.java` | **Hardened** | CRUD + generate + build trigger (SSE stream) + build status + download redirect |
| **Health** | `HealthController.java` | **Week 1** | `GET /conversions/health` |
| **DTOs** | 6 records/classes | **Hardened** | `CreateConversionRequest`, `UpdateConversionRequest`, `ConversionResponse`, `ElectronConfigResponse`, `BuildStatusResponse`, `ErrorResponse` |
| **Exceptions** | `exception/` package | **Week 1** | `ProjectNotFoundException`, `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| **Config** | `R2Properties.java` | **Week 1** | `@ConfigurationProperties(prefix = "webtodesk.r2")` — bucket, keys, endpoint, publicUrl |
| **Config** | `R2ClientConfig.java` | **Week 1** | S3Client bean configured for Cloudflare R2 (path-style, US_EAST_1 region) |
| **Config** | `MongoConfig.java` | **Week 1** | `@EnableMongoAuditing` |
| **Config** | `ConversionSecurityConfig.java` | Stub | Permits all requests (relies on API gateway auth) |
| **Config** | `application.yml` | **Hardened** | Port 8082, MongoDB, Eureka, R2, workspace config — all via env vars |
| **Tests** | 30+ tests | **Week 1** | Service + Controller unit tests |

### 1.2 What's Missing (vs. Target Vision)

| Gap | Priority | Target Week | Status |
|-----|----------|-------------|--------|
| ~~No global exception handler~~ | **P0** | Week 1 | **DONE** |
| ~~No input validation beyond `@NotBlank` / `@URL`~~ | **P0** | Week 1 | **DONE** |
| ~~No unit or integration tests~~ | **P0** | Week 1 | **DONE** (28 tests) |
| ~~MongoDB URI hardcoded~~ | **P0** | Week 1 | **DONE** |
| ~~No health check endpoint~~ | **P0** | Week 1 | **DONE** |
| ~~No R2 artifact storage~~ | **P0** | Week 1 | **DONE** |
| ~~No GitHub Actions build trigger~~ | **P0** | Week 1 | **DONE** (Swapped for Local Build) |
| ~~No webhook callback for build completion~~ | **P0** | Week 1 | **DONE** (Replaced with SSE) |
| ~~No download URL redirect (R2)~~ | **P0** | Week 1 | **DONE** |
| Electron templates are hardcoded strings in `BuildService` | **P1** | Week 2 | Pending |
| No module system (screen-protect, biometric, etc.) | **P1** | Week 2 | Pending |
| No API documentation (OpenAPI) | **P1** | Week 2 | Pending |
| No build history tracking (BuildRecord entity) | **P1** | Week 3 | Pending |
| No licensing system (Trial/Starter/Pro/Lifetime tiers) | **P2** | Week 4 | Pending |
| No license expiry enforcement (blocking screen) | **P2** | Week 4 | Pending |
| No version upgrade system (automatic updates) | **P2** | Week 4 | Pending |
| No OS compatibility matrix (Windows/Linux/Mac) | **P2** | Week 4 | Pending |

### 1.3 Sibling Services (context only — do not modify without explicit request)

| Service | Port | Stack | Purpose |
|---------|------|-------|---------|
| `discovery-service` | 8761 | Spring Boot + Eureka Server | Service registry. Must start first. |
| `api-gateway` | 8080 | Spring Cloud Gateway (reactive/WebFlux) | JWT validation, route mapping, header injection. Routes `/conversion/**` → `lb://conversion-service` with `StripPrefix=1`. |
| `user-service` | 8081 | Spring Boot + PostgreSQL (Neon) + Redis (Upstash) | User CRUD, auth (login/register/refresh/logout), profile management. |
| `frontend` | 5173 | React 19 + Vite 6 + TailwindCSS 3.4 + Framer Motion + Lucide | SPA dashboard. Vite proxies `/user` and `/conversion` to gateway at `:8080`. |
| `common` | — | Maven JAR module | Shared `JwtTokenProvider`, `JwtValidator`, `JwtConstants`. Used by api-gateway and user-service (NOT conversion-service). |

### 1.4 Full Project Architecture & Auth Flow

```
Browser (localhost:5173)
  │
  ├─ /user/**  ──→  Vite proxy ──→ API Gateway (:8080)  ──→ user-service (:8081)
  └─ /conversion/** ──→ Vite proxy ──→ API Gateway (:8080) ──→ conversion-service (:8082)
                                          │
                                    JWT validation via
                                    SecurityContextRepository
                                    + AuthenticationManager
                                          │
                                    HeaderForwardingFilter injects:
                                      X-User-Id, X-User-Email, X-User-Roles
```

### 1.5 Build & Download Flow (Local ProcessBuilder → R2)

```
Frontend ──POST /conversion/conversions/{id}/build──→ API Gateway ──→ Conversion Service
                              │
                              └─ Return 202 BUILDING immediately (@Async)

Conversion Service (async thread — buildExecutor)
  Step 0  VALIDATING_ENV   → probe node/npm versions, check ≥512MB disk
  Step 1  PREPARING        → create temp workspace in BUILD_OUTPUT_DIR
  Step 2  WRITING_FILES    → write config.js, main.js, preload.js, package.json
  Step 3  INSTALLING       → npm install --no-audit --no-fund
  Step 4  BUILDING         → npx electron-builder --{win|linux|mac} --publish=never
  Step 5  FINDING_ARTIFACT → walk dist/ for installer by target extensions
  Step 6  UPLOADING_R2     → upload to R2 key: builds/{email}/{id}/{filename}
          → set status=READY, buildArtifactPath=R2 public URL
  Cleanup → delete temp workspace

Frontend ──SSE /build/stream──→ receives progress events (VALIDATING_ENV → COMPLETE)
Frontend ──GET /build/status──→ polls BuildStatusResponse {status, downloadUrl}
Frontend ──downloads directly from R2 public URL──→ (no Spring proxy)

On failure at any step → status=FAILED, buildError=message, SSE FAILED event
```

**OS / Platform Resolution (`BuildTarget` enum):**
```
WEBTODESK_BUILD_TARGET_PLATFORM=auto  →  Windows host → --win (.exe/.msi)
                                          Linux/Docker → --linux (.AppImage/.deb/.rpm)
auto can be overridden: win | linux | mac | windows | darwin | docker
```

### 1.6 Secrets & Environment Variables

> **CRITICAL:** Never hardcode secrets in code. These are documented for agent awareness only.

**conversion-service (`application.yml`) — actual keys as of 2026-03-28:**
```yaml
server.port: 8082
spring.data.mongodb.uri: ${MONGODB_URI}                             # required, no default
webtodesk.build.output-dir: ${BUILD_OUTPUT_DIR:/tmp/webtodesk-builds}
webtodesk.build.target-platform: ${WEBTODESK_BUILD_TARGET_PLATFORM:auto}
webtodesk.r2.enabled: ${R2_ENABLED:true}
webtodesk.r2.account-id: ${R2_ACCOUNT_ID:b34ccabf01d35919541bffb8a9ad0384}
webtodesk.r2.access-key-id: ${R2_ACCESS_KEY_ID:...}
webtodesk.r2.secret-access-key: ${R2_SECRET_ACCESS_KEY:...}
webtodesk.r2.bucket: ${R2_BUCKET:webtodesk-builds}
webtodesk.r2.public-url: ${R2_PUBLIC_URL:https://pub-1b61d73b19424844a83d743c392ddea5.r2.dev}
webtodesk.r2.endpoint: https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com
eureka.client.service-url.defaultZone: http://localhost:8761/eureka/
```

**Docker ENV vars (set in root `Dockerfile` final stage):**
```
CI=true                          # suppresses interactive npm prompts
ELECTRON_CACHE=/tmp/electron-cache   # electron binary cache across builds
npm_config_cache=/tmp/npm-cache       # npm package cache across builds
ADBLOCK=true                    # suppresses electron-builder ad analytics
```

**R2 Account Details:**
| Key | Value |
|-----|-------|
| Account ID | `b34ccabf01d35919541bffb8a9ad0384` |
| S3 API Endpoint | `https://b34ccabf01d35919541bffb8a9ad0384.r2.cloudflarestorage.com` |
| Bucket | `webtodesk-builds` |
| Public URL | `https://pub-1b61d73b19424844a83d743c392ddea5.r2.dev` |
| GitHub Release Repo | `https://github.com/thecheesybit/web2desk-public-release` |



### 1.7 Frontend Integration Details

**Frontend stack:** React 19, Vite 6, TypeScript 5.7, TailwindCSS 3.4

**Key types (`frontend/src/types/index.ts`):**
```typescript
interface ConversionProject {
  id: string; projectName: string; websiteUrl: string; appTitle: string;
  iconFile: string; currentVersion: string;
  status: 'DRAFT' | 'READY' | 'BUILDING' | 'FAILED';
  createdBy: string; buildError: string | null;
  downloadAvailable: boolean; downloadUrl: string | null;
  createdAt: string; updatedAt: string;
}
interface BuildStatusResponse {
  projectId: string; projectName: string;
  status: 'DRAFT' | 'READY' | 'BUILDING' | 'FAILED';
  buildError: string | null; downloadAvailable: boolean;
  downloadUrl: string | null; updatedAt: string | null;
}
```

**Frontend API calls (`frontend/src/services/api.ts`):**
- `conversionApi.list()` → `GET /conversion/conversions`
- `conversionApi.create(data)` → `POST /conversion/conversions`
- `conversionApi.getById(id)` → `GET /conversion/conversions/${id}`
- `conversionApi.update(id, data)` → `PUT /conversion/conversions/${id}`
- `conversionApi.remove(id)` → `DELETE /conversion/conversions/${id}`
- `conversionApi.generate(id)` → `POST /conversion/conversions/${id}/generate`
- `conversionApi.triggerBuild(id)` → `POST /conversion/conversions/${id}/build`
- `conversionApi.getBuildStatus(id)` → `GET /conversion/conversions/${id}/build/status`
- `conversionApi.subscribeToBuildProgress(id)` → `GET /conversion/conversions/${id}/build/stream`
- `conversionApi.getDownloadUrl(id)` → returns URL string for redirect endpoint

**Download behavior (`DashboardPage.tsx`):**
- If `project.downloadUrl` is set → opens R2 URL directly (`window.open`)
- Else: falls back to `/conversion/conversions/{id}/build/download` which 302-redirects to R2
- Updates via SSE streams progress up to READY state.

---

## 2. STRUCTURED TODO FORMAT

The agent ALWAYS emits this block before coding:

```
## TASK: <one-line description>
## WEEK: <1|2|3|4>
## SCOPE: <which layer(s): entity|service|controller|config|template|pipeline|test>

### TODO
- [ ] P0 · <atomic deliverable>          # P0 = blocking, must ship
- [ ] P1 · <atomic deliverable>          # P1 = core feature
- [ ] P2 · <atomic deliverable>          # P2 = polish / non-blocking
- [ ] TEST · <test / validation step>
- [ ] DOCS · <documentation item>
```

---

## 3. PROJECT STRUCTURE — CURRENT (post-Week 1)

```
conversion-service/
├── pom.xml                          # Spring Boot 3.3 + MongoDB + Security + Eureka + Validation + AWS S3 SDK
├── mvnw / mvnw.cmd                  # Maven wrapper
├── Dockerfile                       # Multi-stage Docker build
├── src/main/java/com/example/conversion_service/
│   ├── ConversionServiceApplication.java
│   ├── config/
│   │   ├── ConversionSecurityConfig.java   # Stub: permitAll (gateway handles auth)
│   │   ├── MongoConfig.java                # @EnableMongoAuditing
│   │   ├── R2Properties.java               # Week 1: R2 bucket/keys/endpoint config
│   │   ├── R2ClientConfig.java             # Week 1: S3Client bean for Cloudflare R2
│   │   └── GitHubProperties.java           # Week 1: GitHub API token/owner/repo/workflow
│   ├── controller/
│   │   ├── ConversionController.java       # 9 endpoints incl. build, callback, download
│   │   └── HealthController.java           # GET /conversions/health
│   ├── dto/
│   │   ├── CreateConversionRequest.java    # Rich validation
│   │   ├── UpdateConversionRequest.java    # Conditional validation
│   │   ├── ConversionResponse.java         # Includes downloadUrl, downloadAvailable
│   │   ├── ElectronConfigResponse.java
│   │   ├── BuildStatusResponse.java        # With downloadUrl + static from() factory
│   │   └── ErrorResponse.java              # Structured error DTO
│   ├── entity/
│   │   └── ConversionProject.java          # 14 fields incl. githubRunId, r2Key, buildArtifactPath
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java     # @RestControllerAdvice (5 handlers)
│   │   └── ProjectNotFoundException.java
│   ├── repository/
│   │   └── ConversionRepository.java
│   └── service/
│       ├── ConversionService.java          # CRUD + generateElectronProject
│       ├── BuildService.java               # Local build orchestrator: env validation, OS-aware npm/npx, BuildTarget enum, 30-min timeout, SSE streaming, R2 upload
│       └── R2StorageService.java           # Upload/delete/exists against R2
├── src/main/resources/
│   ├── application.yml                     # Full config: R2, GitHub, callback, MongoDB, Eureka
│   ├── application-dev.yml
│   └── application-test.yml
└── src/test/java/com/example/conversion_service/
    ├── controller/
    │   └── ConversionControllerTest.java   # 10 tests (needs DTO signature update)
    └── service/
        └── ConversionServiceTest.java      # 18 tests
```

---

## 4. API CONTRACT (current — do not break)

### Endpoints (`/conversions` base path)

```
# CRUD
POST   /conversions                           Create project
GET    /conversions                           List user's projects (via X-User-Email)
GET    /conversions/{id}                      Get project by ID
PUT    /conversions/{id}                      Update project
DELETE /conversions/{id}                      Delete project

# Generation
POST   /conversions/{id}/generate             Generate Electron files (synchronous)

# Build Pipeline (Week 1 — R2 + GitHub Actions)
POST   /conversions/{id}/build                Trigger GitHub Actions build → 202 BUILDING
GET    /conversions/{id}/build/status         Poll build status → BuildStatusResponse
GET    /conversions/{id}/build/download       302 redirect to R2 public URL
POST   /conversions/{id}/build/callback       Webhook from GitHub Actions

# Health
GET    /conversions/health                    Health check
```

### Key Response Shapes

**ConversionResponse** (includes R2 fields):
```json
{
  "id": "string", "projectName": "string", "websiteUrl": "string",
  "appTitle": "string", "iconFile": "string", "currentVersion": "1.0.0",
  "status": "DRAFT|READY|BUILDING|FAILED", "createdBy": "email",
  "buildError": "string|null", "downloadAvailable": true,
  "downloadUrl": "https://pub-xxx.r2.dev/builds/...|null",
  "createdAt": "ISO8601", "updatedAt": "ISO8601"
}
```

**BuildCallbackRequest** (from GitHub Actions):
```json
{
  "projectId": "string", "runId": 12345,
  "success": true, "artifactUrl": "string|null", "errorMessage": "string|null"
}
```

---

## 5. R2 OBJECT KEY STRUCTURE

```
webtodesk-builds/
  builds/
    {user_email}/
      {project_id}/
        {project_name}-Setup.exe         ← Windows NSIS installer  (BuildTarget.WIN)
        {project_name}.AppImage          ← Linux AppImage           (BuildTarget.LINUX)
        {project_name}.dmg               ← macOS DMG                (BuildTarget.MAC)
```

---



## 7. ELECTRON MODULE CONTRACT (target — Week 2)

Every module generated by the template engine MUST implement:

```js
function setup(mainWindow, config) {
  if (!config.modules?.includes('<MODULE_NAME>')) return
  // Implementation
}
function teardown() {}
module.exports = { setup, teardown }
```

### Module Registry (Week 2)

| Module Key | Tier | Description |
|-----------|------|-------------|
| `screen-protect` | Pro | `setContentProtection(true)` |
| `biometric` | Pro | Touch ID / Windows Hello |
| `offline` | Free | Offline detection + error page |
| `badge` | Free | `app.setBadgeCount(n)` via IPC |
| `deep-link` | Pro | Custom protocol registration |

---

## 8. FOUR-WEEK OVERHAUL PLAN

### WEEK 1 — FOUNDATION + R2 + LOCAL BUILDS ✅ COMPLETE (2026-03-27)

**Goal:** Production-worthy error handling, validation, tests, health check,
R2 cloud storage, local build orchestration using Node.js & Electron Builder, SSE streaming, and frontend integration.

```
## STATUS: ✅ COMPLETE (code deployed)
## Tests: 30+ passing

### DONE
- [x] GlobalExceptionHandler, ProjectNotFoundException, ErrorResponse
- [x] Rich validation on Create/Update DTOs
- [x] Externalized MongoDB URI, dev/test profiles
- [x] HealthController (GET /conversions/health)
- [x] Unit/Integration Tests
- [x] R2Properties + R2ClientConfig (S3Client bean for Cloudflare R2)
- [x] R2StorageService (upload file/stream, delete, exists, getPublicUrl)
- [x] Local workspace configuration (`build.workspace.dir`)
- [x] BuildService refactoring: Generic ProcessBuilder + SSE Server-Sent Events for output streaming
- [x] ConversionProject entity fields: `r2Key`, `buildArtifactPath`, `buildError`
- [x] Frontend features: R2 direct download link, @microsoft/fetch-event-source for robust SSE
- [x] Tested & working E2E Pipeline (Frontend → Backend builds `.exe` locally → Uploads to R2 → Downloads from UI)

### CROSS-PLATFORM + DOCKER FIX (2026-03-28) ✅
- [x] Removed `cmd /c` hardcoding — `resolveExecutable()` appends `.cmd` only on Windows
- [x] `BuildTarget` enum: WIN/LINUX/MAC with preferred installer extensions per platform
- [x] `resolveBuildTarget()`: auto-detects OS, falls back to LINUX in Docker/container
- [x] `validateBuildEnvironment()`: pre-flight check for node/npm + 512MB disk space
- [x] `buildEnvironment()`: augments PATH with `/usr/local/bin:/usr/bin:/bin`, sets HOME, ELECTRON_CACHE, CI=true
- [x] `getToolVersion()`: safe 10s tool probe with try-with-resources (no resource leak)
- [x] `runProcess()`: uses `buildEnvironment()`, 30-min process timeout, captures last 20 lines on failure
- [x] `application.yml`: added `webtodesk.build.target-platform` config key
- [x] Root `Dockerfile`: added `nodejs npm python3 build-base` to Alpine apk; pre-created `/tmp/electron-cache` and `/tmp/npm-cache`; set `CI`, `ELECTRON_CACHE`, `npm_config_cache`, `ADBLOCK` ENV vars
- [x] Artifact discovery: extension-aware for .exe/.msi/.AppImage/.deb/.rpm/.dmg/.zip

### NOT YET DEPLOYED
(All changes above local — deploy to Docker/Hugging Face Space to test end-to-end.)
```

### WEEK 2 — FRONTEND INTEGRATION + LICENSING BACKEND ✅ COMPLETE (2026-03-28)

**Goal**: Implement complete frontend integration with licensing system, build queue management, and real-time monitoring.

```
## STATUS: ✅ COMPLETE
## Tests: 57/57 passing (10 ConversionControllerTest + 12 LicenseControllerTest + 18 ConversionServiceTest + 17 LicenseServiceTest)

### DONE — Backend Licensing Services
- [x] LicenseTier enum + license fields added to ConversionProject entity
      (tier, licenseExpiresAt, buildCount, maxBuilds, licenseId — all nullable, existing docs stay valid)
- [x] License DTOs: LicenseInfoResponse, LicenseDashboardResponse, LicenseValidationResponse,
      LicenseRestrictionsResponse, LicenseUsageStatsResponse, UpgradeOptionResponse,
      ValidateLicenseRequest, InitiateUpgradeRequest, InitiateUpgradeResponse, CompleteUpgradeRequest
- [x] LicenseViolationException → GlobalExceptionHandler returns 402 PAYMENT_REQUIRED
- [x] LicenseService.java — tier validation, 60s in-memory cache, build quota enforcement,
      dashboard, usage stats, feature availability, upgrade stub, refresh
- [x] BuildQueueService.java — ConcurrentMap active-build tracking, normal/priority depth counters,
      queue status (length + avg wait estimate)
- [x] LicenseController.java at /license/** — 10 endpoints matching licenseApi.ts:
      GET /current, GET /dashboard, POST /validate, GET /upgrade-options,
      POST /upgrade, POST /upgrade/complete, GET /usage, GET /features/{id}/availability,
      GET /restrictions, POST /refresh
- [x] BuildController.java at /build/** — 13 endpoints matching buildApi.ts:
      POST /trigger, GET /status/{id}, GET /progress/{id} (SSE), GET /queue/status,
      POST /cancel/{id}, POST /retry/{id}, GET /file-types/{os}, POST /validate-config,
      GET /metrics, GET /history/{id}, GET /download/{id}, GET /logs/{id}
- [x] BuildService updated: validateBuildRequest() before build, recordBuildStarted/Finished()
      around build, buildCount incremented on successful artifact upload
- [x] No Redis dependency added — in-memory ConcurrentHashMap cache with 60s TTL

### DONE — Frontend UI Components
- [x] types/license.ts, types/build.ts, types/modules.ts, types/upgrade.ts — all present pre-Week2
- [x] services/licenseApi.ts, services/buildApi.ts, services/versionApi.ts — present pre-Week2
- [x] hooks/useLicense.ts, hooks/useBuildQueue.ts — present pre-Week2
- [x] components/LicenseBadge.tsx — tier badge with compact mode + expiry warning
- [x] components/BuildProgress.tsx — SSE-connected progress bar with stage chips
- [x] components/FeatureToggle.tsx — blurred locked state + upgrade overlay + UpgradeModal trigger
- [x] components/UpgradeModal.tsx — 3-plan upgrade cards (Starter/Pro/Lifetime) + Stripe stub

### NOT DONE (deferred to Week 3)
- [ ] ProjectWizard multi-step component (moved to Week 3 with module system)
- [ ] Redis cache (no Redis infra available; in-memory cache sufficient for now)
- [ ] MongoDB indexes for license fields (deferred; low-volume MVP)
- [ ] VersionUpgradeService (deferred to Week 3)
```

### WEEK 3 — MODULE SYSTEM + BUILD PIPELINE (Days 15–21)

**Goal**: Implement configurable module system with template engine and advanced build pipeline features.

```
## TASK: Template Engine Implementation
## WEEK: 3
## SCOPE: service, template, config

### TODO
- [ ] P0 · Add Mustache dependency to conversion-service pom.xml
- [ ] P0 · Create TemplateEngine.java with caching
- [ ] P0 · Extract 4 Electron templates from BuildService to .mustache files
- [ ] P0 · Create template registry for module injection points
- [ ] P1 · Refactor BuildService.generateFiles() to use TemplateEngine
- [ ] TEST · Template output matches hardcoded output
- [ ] TEST · Template caching performance tests

## TASK: Module System Architecture
## WEEK: 3
## SCOPE: entity, service, dto, template

### TODO
- [ ] P0 · Create ModuleRegistry.java with tier-based availability
- [ ] P0 · Create ModuleConfig.java for feature configuration
- [ ] P0 · Implement 15 core modules (splash-screen, file-download, etc.)
- [ ] P0 · Create module templates with setup/teardown contracts
- [ ] P1 · Add module validation and dependency checking
- [ ] P1 · Create module configuration UI components
- [ ] TEST · Module registry validation tests
- [ ] TEST · Module injection point tests
- [ ] TEST · Tier-based module availability tests

## TASK: Advanced Build Pipeline
## WEEK: 3
## SCOPE: service, config, metrics

### TODO
- [ ] P0 · Implement cross-platform build orchestration
- [ ] P0 · Add BuildMetrics.java for performance tracking
- [ ] P0 · Create FileTypeResolver with OS-specific mappings
- [ ] P0 · Implement build queue with priority executors
- [ ] P1 · Add build history tracking with BuildRecord entity
- [ ] P1 · Implement build retry and cancellation
- [ ] TEST · Cross-platform build tests
- [ ] TEST · Build queue performance tests
- [ ] TEST · Build metrics accuracy tests
```

### WEEK 4 — PRODUCTION HARDENING + MONITORING (Days 22–30)

**Goal**: Production deployment with monitoring, observability, and advanced licensing features.

```
## TASK: Production Deployment
## WEEK: 4
## SCOPE: config, docker, deployment

### TODO
- [ ] P0 · Create production Docker configurations
- [ ] P0 · Implement health checks with detailed status
- [ ] P0 · Add application metrics and monitoring
- [ ] P0 · Configure production logging and error tracking
- [ ] P1 · Set up CI/CD pipeline with GitHub Actions
- [ ] P1 · Implement database backup and recovery
- [ ] TEST · Production deployment validation
- [ ] TEST · Load testing for build queue
- [ ] TEST · Failover and recovery tests

## TASK: Advanced Licensing Features
## WEEK: 4
## SCOPE: service, controller, frontend

### TODO
- [ ] P0 · Implement license expiry enforcement with blocking screen
- [ ] P0 · Add automatic version upgrade system
- [ ] P0 · Create license analytics and reporting dashboard
- [ ] P0 · Implement subscription billing integration (Stripe/Razorpay)
- [ ] P1 · Add license transfer and team management
- [ ] P1 · Create admin panel for license management
- [ ] TEST · License expiry enforcement tests
- [ ] TEST · Version upgrade migration tests
- [ ] TEST · Billing integration tests

## TASK: Monitoring & Observability
## WEEK: 4
## SCOPE: config, service, metrics

### TODO
- [ ] P0 · Implement Prometheus metrics collection
- [ ] P0 · Add distributed tracing with Spring Cloud Sleuth
- [ ] P0 · Create custom dashboards for build and license metrics
- [ ] P0 · Set up alerting for critical failures
- [ ] P1 · Add performance profiling and optimization
- [ ] P1 · Implement security monitoring and audit logging
- [ ] TEST · Metrics accuracy validation
- [ ] TEST · Alerting system tests
- [ ] TEST · Security audit tests
```

### WEEK 5+ — ADVANCED FEATURES & OPTIMIZATION (Days 31+)

**Goal**: Advanced features, performance optimization, and scalability improvements.

```
## TASK: Scalability Improvements
## WEEK: 5+
## SCOPE: architecture, service, infrastructure

### TODO
- [ ] P1 · Implement microservice scaling with Kubernetes
- [ ] P1 · Add database sharding for large-scale deployments
- [ ] P1 · Implement CDN integration for build artifacts
- [ ] P1 · Add edge caching for API responses
- [ ] P2 · Implement multi-region deployment
- [ ] P2 · Add advanced security features (2FA, SSO)
- [ ] TEST · Scalability and performance tests
- [ ] TEST · Multi-region failover tests

## TASK: Advanced Features
## WEEK: 5+
## SCOPE: service, frontend, integration

### TODO
- [ ] P1 · Add AI-powered build optimization
- [ ] P1 · Implement custom branding and white-labeling
- [ ] P1 · Create plugin system for third-party integrations
- [ ] P1 · Add advanced analytics and reporting
- [ ] P2 · Implement mobile app management
- [ ] P2 · Add API rate limiting and quota management
- [ ] TEST · Advanced feature integration tests
- [ ] TEST · Plugin system tests
```

---

## 14. IMPLEMENTATION ROADMAP - WEEK 2+ DETAILED PLAN

Based on the current Week 1 completion status and licensing system architecture, here's the comprehensive implementation plan for Week 2 and beyond:

### 14.1 Current Status Summary

**Week 1 ✅ COMPLETE:**
- Foundation: Error handling, validation, tests, health checks
- Build System: Local Node.js builds with cross-platform support
- Storage: Cloudflare R2 integration with artifact upload
- Frontend: Basic React dashboard with SSE streaming
- Docker: Multi-stage builds with proper tooling

**Ready for Week 2:**
- Licensing system architecture designed
- Frontend integration components specified
- Build queue architecture planned
- Database schema defined

### 14.2 Week 2 Priority Matrix

| Priority | Component | Deliverables | Dependencies | Risk |
|----------|-----------|--------------|--------------|------|
| **P0-Critical** | Backend Licensing | LicenseService, BuildQueueService, VersionUpgradeService | MongoDB schema, Redis cache | Medium |
| **P0-Critical** | Frontend Integration | TypeScript types, API services, React hooks | Backend APIs | Low |
| **P0-Critical** | Database Updates | License fields, indexes, migration script | Entity updates | Low |
| **P1-Important** | UI Components | ProjectWizard, LicenseBadge, BuildProgress | Frontend integration | Low |
| **P1-Important** | SSE Implementation | Real-time build progress streaming | BuildQueueService | Medium |

### 14.3 Week 2 Daily Breakdown

**Day 8-9: Backend Licensing Foundation**
- Create LicenseService with tier validation
- Implement BuildQueueService with priority routing
- Add license DTOs and update ConversionProject entity
- Set up Redis caching for license data

**Day 10-11: Frontend Integration**
- Create TypeScript types (license.ts, build.ts, modules.ts, upgrade.ts)
- Implement API services (licenseApi.ts, buildApi.ts, versionApi.ts)
- Create React hooks (useLicense.ts, useBuildQueue.ts)
- Add basic UI components (LicenseBadge, BuildProgress)

**Day 12-13: Advanced Features**
- Implement VersionUpgradeService with license persistence
- Add SSE streaming for real-time build monitoring
- Create multi-step ProjectWizard component
- Add FeatureToggle with upgrade prompts

**Day 14: Integration & Testing**
- End-to-end testing of complete license flow
- Build queue monitoring with mock SSE events
- Performance testing for license validation
- Documentation updates

### 14.4 Success Criteria for Week 2

**Functional Requirements:**
- ✅ Complete license tier validation (Trial/Starter/Pro/Lifetime)
- ✅ Real-time build queue monitoring with SSE
- ✅ Frontend multi-step project creation wizard
- ✅ License upgrade flow with payment integration
- ✅ Cross-platform build configuration

**Technical Requirements:**
- ✅ All 34 new API endpoints implemented and tested
- ✅ Frontend components fully integrated with backend
- ✅ Database migration script executed successfully
- ✅ Redis caching configured and operational
- ✅ SSE streaming working for build progress

**Performance Requirements:**
- ✅ License validation response time < 100ms
- ✅ Build queue routing < 50ms
- ✅ Frontend component render time < 200ms
- ✅ SSE latency < 500ms

### 14.5 Risk Mitigation Strategies

**Technical Risks:**
- **Database Migration Failure:** Create backup script, test on staging first
- **Redis Cache Issues:** Implement fallback to direct database queries
- **SSE Connection Issues:** Add reconnection logic with exponential backoff
- **Frontend State Management:** Use React Query for server state synchronization

**Integration Risks:**
- **API Contract Changes:** Version APIs with semantic versioning
- **License Validation Logic:** Implement comprehensive test suite
- **Build Queue Deadlocks:** Add timeout and retry mechanisms
- **Frontend-Backend Sync:** Implement optimistic updates with rollback

### 14.6 Week 3-4 Preview

**Week 3: Module System & Template Engine**
- Mustache template implementation
- 15 core modules with tier-based availability
- Advanced build pipeline with metrics
- Module configuration UI

**Week 4: Production Hardening**
- License expiry enforcement
- Subscription billing integration
- Monitoring and observability
- Production deployment

### 14.7 Resource Requirements

**Development Resources:**
- 1 Full-stack developer (Week 2)
- 1 Frontend specialist (Week 2-3)
- 1 Backend specialist (Week 2-4)
- 1 DevOps engineer (Week 4)

**Infrastructure Resources:**
- Redis instance for caching
- MongoDB Atlas with proper indexing
- Cloudflare R2 for build artifacts
- Monitoring stack (Prometheus + Grafana)

**Testing Resources:**
- Staging environment for integration testing
- Load testing tools for build queue
- Automated testing pipeline
- Performance monitoring

---

**OS-Specific Build Flags & Priority Queue:**
```java
// BuildFlags record for OS-specific configuration
public record BuildFlags(
    TargetOS targetOS,           // WINDOWS, LINUX, MACOS
    BuildPriority priority,      // NORMAL, PRIORITY
    FileType fileType,           // EXE, MSI, APPIMAGE, DEB, RPM, DMG, ZIP
    boolean crossPlatform,       // true = build for all OS
    Map<TargetOS, FileType> osFileMappings  // OS → specific file type
) {}

// Priority queue routing service
@Service
public class BuildQueueService {
    
    @Autowired
    @Qualifier("buildExecutorNormal")
    private Executor normalExecutor;  // Trial tier: 1 thread, 50 capacity
    
    @Autowired
    @Qualifier("buildExecutorPriority") 
    private Executor priorityExecutor; // Pro tiers: 5 threads, instant dispatch
    
    public CompletableFuture<BuildResult> routeBuild(ConversionProject project) {
        // License validation first
        licenseService.validateBuildRequest(project);
        
        BuildFlags flags = project.getBuildFlags();
        
        // Resolve file type and route to appropriate queue
        Executor executor = flags.priority() == BuildPriority.PRIORITY 
            ? priorityExecutor : normalExecutor;
            
        return CompletableFuture.supplyAsync(() -> {
            return buildService.triggerBuild(project, flags);
        }, executor);
    }
}
```

**Enhanced ConversionProject Entity:**
```java
@Document(collection = "conversionprojects")
public class ConversionProject {
    
    // Existing fields...
    private String id, projectName, websiteUrl, appTitle, iconFile, currentVersion;
    private ConversionStatus status;
    private String createdBy, buildError, downloadUrl, r2Key, buildArtifactPath;
    private Instant createdAt, updatedAt;
    
    // NEW: Licensing fields
    private LicenseTier tier;
    private Instant licenseExpiresAt;
    private Integer buildCount, maxBuilds, activeAppsCount;
    
    // NEW: Build configuration
    private BuildFlags buildFlags;
    private ModuleRegistry moduleRegistry;
    private LicenseMetadata licenseMetadata;
    
    // NEW: Feature configuration (JSON)
    @Column(columnDefinition = "TEXT")
    private FeatureConfig featureConfig;
}
```

**Database Schema Updates for Licensing & OS Features:**
```sql
-- ConversionProject collection updates
db.conversionprojects.updateMany(
  {},
  {
    $set: {
      tier: "TRIAL",
      license_expires_at: new Date(Date.now() + 30*24*60*60*1000),
      build_count: 0,
      max_builds: 4,
      active_apps_count: 0,
      build_flags: {
        targetOS: "WINDOWS",
        priority: "NORMAL", 
        fileType: "WINDOWS_EXE",
        crossPlatform: false,
        osFileMappings: {}
      },
      module_registry: {
        enabled: ["splash-screen", "file-download"],
        disabled: [],
        config: {}
      },
      license_metadata: {
        licenseId: UUID().toString(),
        issuedAt: new Date(),
        lastValidatedAt: new Date(),
        migrationHistory: []
      },
      feature_config: {
        tier: "TRIAL",
        buildFlags: {
          targetOS: "WINDOWS",
          priority: "NORMAL",
          fileType: "WINDOWS_EXE",
          crossPlatform: false,
          osFileMappings: {}
        },
        modules: [],
        // ... other feature configs
      }
    }
  }
);

-- Performance indexes
db.conversionprojects.createIndex({ "tier": 1 });
db.conversionprojects.createIndex({ "license_expires_at": 1 });
db.conversionprojects.createIndex({ "build_flags.targetOS": 1 });
db.conversionprojects.createIndex({ "build_flags.priority": 1 });
db.conversionprojects.createIndex({ "created_by": 1, "build_count": 1 });
```

**Optimized File Type Detection:**
```java
@Component
public class FileTypeResolver {
    
    private static final Map<TargetOS, Map<String, FileType>> OS_FILE_MAPPINGS = Map.of(
        TargetOS.WINDOWS, Map.of(
            "exe", FileType.WINDOWS_EXE,
            "msi", FileType.WINDOWS_MSI
        ),
        TargetOS.LINUX, Map.of(
            "appimage", FileType.LINUX_APPIMAGE,
            "deb", FileType.LINUX_DEB, 
            "rpm", FileType.LINUX_RPM
        ),
        TargetOS.MACOS, Map.of(
            "dmg", FileType.MACOS_DMG,
            "zip", FileType.MACOS_ZIP
        )
    );
    
    public FileType resolveFileType(TargetOS os, FileType requestedType) {
        return requestedType != null ? requestedType : getDefaultFileType(os);
    }
    
    public String getElectronBuilderTarget(TargetOS os) {
        return switch (os) {
            case WINDOWS -> "--win";
            case LINUX -> "--linux";
            case MACOS -> "--mac";
        };
    }
}
```

## 15. OPTIMIZED BUILD PIPELINE ARCHITECTURE

### 15.1 High-Performance Queue Routing

**Optimized BuildQueueService with caching and batching:**
```java
@Service
public class BuildQueueService {
    
    private final ConcurrentMap<String, CompletableFuture<BuildResult>> activeBuilds = new ConcurrentHashMap<>();
    private final BuildMetrics buildMetrics;
    
    @Autowired
    @Qualifier("buildExecutorNormal")
    private ThreadPoolTaskExecutor normalExecutor;  // 1 thread, 50 capacity
    
    @Autowired
    @Qualifier("buildExecutorPriority") 
    private ThreadPoolTaskExecutor priorityExecutor; // 5 threads, 100 capacity
    
    public CompletableFuture<BuildResult> routeBuild(ConversionProject project) {
        String buildKey = project.getId() + "_" + project.getBuildFlags().targetOS();
        
        // Check for duplicate builds
        if (activeBuilds.containsKey(buildKey)) {
            return activeBuilds.get(buildKey);
        }
        
        // License validation with caching
        licenseService.validateWithCache(project);
        
        BuildFlags flags = project.getBuildFlags();
        Executor executor = selectOptimalExecutor(flags, project.getTier());
        
        CompletableFuture<BuildResult> buildFuture = CompletableFuture.supplyAsync(() -> {
            try {
                buildMetrics.recordBuildStart(project.getTier(), flags.targetOS());
                BuildResult result = buildService.triggerBuild(project, flags);
                buildMetrics.recordBuildSuccess(project.getTier(), flags.targetOS());
                return result;
            } catch (Exception e) {
                buildMetrics.recordBuildFailure(project.getTier(), flags.targetOS());
                throw e;
            } finally {
                activeBuilds.remove(buildKey);
            }
        }, executor);
        
        activeBuilds.put(buildKey, buildFuture);
        return buildFuture;
    }
    
    private Executor selectOptimalExecutor(BuildFlags flags, LicenseTier tier) {
        // Priority queue for Pro tiers + cross-platform builds
        if (flags.priority() == BuildPriority.PRIORITY || flags.crossPlatform()) {
            return priorityExecutor;
        }
        
        // Load balancing: if normal queue is full, route to priority if user is Pro+
        if (tier != LicenseTier.TRIAL && normalExecutor.getActiveCount() >= normalExecutor.getCorePoolSize()) {
            return priorityExecutor;
        }
        
        return normalExecutor;
    }
}
```

### 15.2 CORS Localhost Pattern Fix

**Problem:** Browser accessing app at `http://localhost:8090` gets 403 Forbidden because `GatewaySecurityConfig` only allowed `http://localhost:7860`.

**Solution:** Replace static origins with wildcard localhost pattern:

```java
// Before (GatewaySecurityConfig.java)
config.setAllowedOrigins(List.of(
    "http://localhost:3000",
    "http://localhost:5173",
    "http://localhost:7860",
    "https://webtodesk.onrender.com"
));

// After
config.setAllowedOriginPatterns(List.of(
    "http://localhost:[*]",  // Matches any localhost port
    "https://*.onrender.com",
    "https://*.hf.space",
    "https://*.static.hf.space"
));
```

**Result:** Browser `Origin: http://localhost:8090` now accepted, login works correctly.

### 15.3 Intelligent File Type Resolution

**Enhanced FileTypeResolver with caching and validation:**
```java
@Component
public class FileTypeResolver {
    
    private final Cache<String, FileType> fileTypeCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofHours(1))
        .build();
    
    private static final Map<TargetOS, Map<String, FileType>> OS_FILE_MAPPINGS = Map.of(
        TargetOS.WINDOWS, Map.of(
            "exe", FileType.WINDOWS_EXE,
            "msi", FileType.WINDOWS_MSI
        ),
        TargetOS.LINUX, Map.of(
            "appimage", FileType.LINUX_APPIMAGE,
            "deb", FileType.LINUX_DEB, 
            "rpm", FileType.LINUX_RPM
        ),
        TargetOS.MACOS, Map.of(
            "dmg", FileType.MACOS_DMG,
            "zip", FileType.MACOS_ZIP
        )
    );
    
    public FileType resolveFileType(TargetOS os, FileType requestedType) {
        String cacheKey = os.name() + "_" + (requestedType != null ? requestedType.name() : "default");
        
        return fileTypeCache.get(cacheKey, key -> {
            if (requestedType != null && isValidFileTypeForOS(os, requestedType)) {
                return requestedType;
            }
            return getDefaultFileType(os);
        });
    }
    
    public BuildTarget resolveElectronTarget(BuildFlags flags) {
        TargetOS os = flags.targetOS();
        FileType fileType = resolveFileType(os, flags.fileType());
        
        return switch (os) {
            case WINDOWS -> {
                if (fileType == FileType.WINDOWS_MSI) yield BuildTarget.WIN_MSI;
                else yield BuildTarget.WIN_EXE;
            }
            case LINUX -> {
                yield switch (fileType) {
                    case LINUX_DEB -> BuildTarget.LINUX_DEB;
                    case LINUX_RPM -> BuildTarget.LINUX_RPM;
                    default -> BuildTarget.LINUX_APPIMAGE;
                };
            }
            case MACOS -> {
                if (fileType == FileType.MACOS_ZIP) yield BuildTarget.MAC_ZIP;
                else yield BuildTarget.MAC_DMG;
            }
        };
    }
    
    private boolean isValidFileTypeForOS(TargetOS os, FileType fileType) {
        return OS_FILE_MAPPINGS.getOrDefault(os, Map.of()).containsValue(fileType);
    }
}
```

### 15.3 Cross-Platform Build Optimization

**Parallel build orchestrator for multiple OS targets:**
```java
@Service
public class CrossPlatformBuildService {
    
    public CompletableFuture<Map<TargetOS, BuildResult>> buildForAllPlatforms(ConversionProject project) {
        Map<TargetOS, CompletableFuture<BuildResult>> platformBuilds = new HashMap<>();
        
        // Trigger parallel builds for all supported platforms
        for (TargetOS os : TargetOS.values()) {
            BuildFlags osFlags = project.getBuildFlags().withTargetOS(os);
            ConversionProject osProject = project.withBuildFlags(osFlags);
            
            platformBuilds.put(os, buildQueueService.routeBuild(osProject));
        }
        
        // Combine all results
        return CompletableFuture.allOf(platformBuilds.values().toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<TargetOS, BuildResult> results = new HashMap<>();
                platformBuilds.forEach((os, future) -> {
                    try {
                        results.put(os, future.get());
                    } catch (Exception e) {
                        results.put(os, BuildResult.failed(os, e.getMessage()));
                    }
                });
                return results;
            });
    }
    
    public List<FileType> getOptimalFileTypes(TargetOS os, LicenseTier tier) {
        // Return optimal file types based on tier and OS
        return switch (tier) {
            case TRIAL -> List.of(getDefaultFileType(os));
            case STARTER -> List.of(getDefaultFileType(os));
            case PRO, LIFETIME -> getAllFileTypesForOS(os);
        };
    }
}
```

### Java / Spring Boot
- `mvn clean test -pl conversion-service` passes with 0 failures
- No `RuntimeException` thrown directly — use typed exceptions
- All endpoints return structured `ErrorResponse` on failure
- All `@RequestBody` DTOs have `@Valid`
- No hardcoded secrets — all via `${ENV_VAR:default}`
- New entity fields are nullable — existing documents stay valid
- Controller methods return `ResponseEntity` with explicit status codes

### Electron Templates
- `nodeIntegration: false`, `contextIsolation: true` in all BrowserWindow configs
- `preload` path uses `path.join(__dirname, ...)` — never concatenation
- No `eval()` in generated main process code

### Build Pipeline
- Failed builds set FAILED status with error — never silently drop
- Build process timeout: 30 min enforced via `process.waitFor(30, TimeUnit.MINUTES)` → `destroyForcibly()` → IOException → FAILED status
- Pre-flight env check runs before workspace creation — fail fast on missing Node.js or low disk space

---

## 10. COMMON PITFALLS — NEVER DO THESE

| Anti-pattern | Correct approach |
|-------------|-----------------|
| Throwing raw `RuntimeException` | Use typed exceptions |
| Hardcoding MongoDB URI | Use `${MONGODB_URI:...}` |
| Adding required fields to entity | New fields MUST be nullable |
| Deleting existing endpoints | Only add. Deprecate with `@Deprecated`. |
| Generating `nodeIntegration: true` | Always `false` |
| Synchronous GitHub API in request thread | Use `@Async` — return 202 immediately |
| Frontend sends to `/conversions/...` directly | Frontend sends to `/conversion/conversions/...` — gateway strips prefix |
| Using `@PathVariable` without explicit name | Always `@PathVariable("id")` |
| `npm install` instead of `npm ci` in CI | `npm ci` for deterministic builds |
| Hardcoding `cmd /c` or forcing `--win` in build steps | Resolve command/target by OS (`auto` or explicit `webtodesk.build.target-platform`) |
| Skipping `nodejs npm` in final Docker image | Final stage is `eclipse-temurin:17-jre-alpine` — it has NO Node.js. Add to `apk add` in `Dockerfile` |
| Using raw `System.getenv()` in ProcessBuilder | Use `buildEnvironment()` which augments PATH, sets HOME, ELECTRON_CACHE, CI |
| Not validating build env before spawning processes | Always call `validateBuildEnvironment()` first — catches missing npm before touching disk |
| Streaming large artifacts through Spring | Use R2 redirect (302) |

---

## 11. TROUBLESHOOTING

| Problem | Fix |
|---------|-----|
| `Cannot run program "npm"` error=2 No such file | Node.js not installed in final Docker image. Add `nodejs npm python3 build-base` to `apk add` in root `Dockerfile` |
| `npm` found locally but not in Docker | PATH seen by JVM in Docker differs from shell PATH. `buildEnvironment()` prepends `/usr/local/bin:/usr/bin:/bin` to fix |
| Build fails with no useful error message | Check logs for last 20 lines — `runProcess()` logs them at ERROR level on non-zero exit |
| Build silently hangs | 30-min process timeout will fire, destroy process, and emit FAILED status. Check Docker memory limits if builds die early |
| `ELECTRON_CACHE` permission denied | `/tmp/electron-cache` must exist and be writable. Created at Docker build time via `mkdir -p` |
| AppImage build fails in Docker | May need FUSE kernel module. Fallback: set `WEBTODESK_BUILD_TARGET_PLATFORM=linux` and ensure `appimagetool` can run. Check Docker `--privileged` flag if on self-hosted runner |
| `SignatureDoesNotMatch` on R2 upload | Check `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY` — no spaces |
| `NoSuchBucket` error | Bucket must be exactly `webtodesk-builds` |
| R2 URL returns 403 | Enable public access on bucket in Cloudflare dashboard |
| Stale Java classes | Always `mvn clean compile` before `spring-boot:run` |
| Port 8082 conflict | `Get-Process -Name java \| Stop-Process -Force` |

---

---

## 12. SESSION LEARNINGS (2026-03-28 — Docker + Cross-Platform Build Fix)

### Problem That Was Solved

`Cannot run program "npm" (in directory "/tmp/webtodesk-builds/build-..."):
error=2, No such file or directory`

Triggered by: frontend hitting `POST /conversion/conversions/{id}/build` in Docker deployment.

### Root Causes (in order of impact)

1. **No Node.js in Docker image** — `eclipse-temurin:17-jre-alpine` is JRE only. `npm` binary simply didn't exist.
2. **`cmd /c` hardcoded** — original `runNpmInstall`/`runElectronBuilder` used `new String[]{"cmd", "/c", "npm", ...}` which is Windows-cmd-only and fails immediately on Linux.
3. **No pre-flight check** — build went straight to workspace creation, so the failure wasn't actionable.
4. **`System.getenv()` passed raw** — JVM's inherited PATH inside Docker may not include `/usr/local/bin` or `/usr/bin`, hiding tools that ARE installed.
5. **No process timeout** — a hung npm download or electron-builder step would block the async thread forever.
6. **No error tail capture** — failed builds showed only "exit code 1" with zero context.

### Fixes Applied

| Fix | File |
|-----|------|
| Add `nodejs npm python3 build-base` to `apk add` | `Dockerfile` (root) |
| Set `CI`, `ELECTRON_CACHE`, `npm_config_cache`, `ADBLOCK` ENV | `Dockerfile` (root) |
| `BuildTarget` enum (WIN/LINUX/MAC) + `resolveBuildTarget()` | `BuildService.java` |
| `resolveExecutable()` — `.cmd` on Windows, bare on Linux | `BuildService.java` |
| `validateBuildEnvironment()` — probe node/npm, check disk | `BuildService.java` |
| `buildEnvironment()` — augmented PATH + HOME + ELECTRON_CACHE | `BuildService.java` |
| `getToolVersion()` — safe 10s probe, try-with-resources | `BuildService.java` |
| `runProcess()` — 30-min timeout, tail output on failure | `BuildService.java` |
| `webtodesk.build.target-platform` config key | `application.yml` |

### Key Principle

> The final Docker image is the runtime for ALL services in the container. Every tool that any service needs to spawn as a subprocess (node, npm, npx) **must be installed at the image level** — not assumed from the developer's local machine.

---

---

## 13. LICENSE EXPIRY ENFORCEMENT ARCHITECTURE

### 13.1 Runtime License Check

**Implementation in generated main.js:**
```javascript
// INJECT: license expiry check (added to main.js)
const { licenseExpiresAt, upgradeUrl, licenseTier } = require('./config');

function checkLicenseExpiry(mainWindow) {
  const now = Date.now();
  const expiryTime = new Date(licenseExpiresAt).getTime();
  
  if (now > expiryTime) {
    // License expired - show blocking screen
    mainWindow.loadFile(path.join(__dirname, 'license-expired.html'));
    mainWindow.setResizable(false);
    mainWindow.setMenuBarVisibility(false);
    mainWindow.setMaximizable(false);
    mainWindow.setFullScreenable(false);
    
    // Prevent dev tools on expired screen
    mainWindow.webContents.setDevToolsWebContents(null);
    
    return true; // expired
  }
  return false; // valid
}

// Call before loading website URL
if (checkLicenseExpiry(mainWindow)) {
  return; // don't load websiteUrl
}
```

### 13.2 License Expired Screen

**license-expired.html (bundled in build assets):**
```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>License Expired - WebToDesk</title>
  <style>
    body {
      margin: 0;
      padding: 0;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
    }
    .container {
      text-align: center;
      max-width: 500px;
      padding: 40px;
    }
    .icon {
      font-size: 64px;
      margin-bottom: 20px;
    }
    h1 {
      font-size: 32px;
      margin-bottom: 16px;
    }
    p {
      font-size: 18px;
      margin-bottom: 32px;
      opacity: 0.9;
    }
    .upgrade-btn {
      background: white;
      color: #667eea;
      padding: 12px 32px;
      border: none;
      border-radius: 8px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      display: inline-block;
      transition: transform 0.2s;
    }
    .upgrade-btn:hover {
      transform: scale(1.05);
    }
    .tier-info {
      margin-top: 40px;
      padding: 20px;
      background: rgba(255,255,255,0.1);
      border-radius: 8px;
      font-size: 14px;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="icon">⏰</div>
    <h1>License Expired</h1>
    <p>Your WebToDesk license has expired. Upgrade to continue using your desktop apps.</p>
    <a href="${upgradeUrl}" class="upgrade-btn" target="_blank">Upgrade Now</a>
    <div class="tier-info">
      <strong>Current Tier:</strong> ${licenseTier}<br>
      <strong>Expired:</strong> ${licenseExpiresAt}
    </div>
  </div>
  <script>
    // Prevent navigation away from expired screen
    window.addEventListener('beforeunload', (e) => {
      e.preventDefault();
      e.returnValue = '';
    });
    
    // Disable right-click context menu
    document.addEventListener('contextmenu', (e) => {
      e.preventDefault();
    });
    
    // Disable keyboard shortcuts
    document.addEventListener('keydown', (e) => {
      if (e.key === 'F12' || (e.ctrlKey && e.shiftKey && e.key === 'I')) {
        e.preventDefault();
      }
    });
  </script>
</body>
</html>
```

### 13.3 Backend License Validation

**LicenseService.java validation flow:**
```java
@Service
public class LicenseService {
    
    public void validateBuildRequest(ConversionProject project) throws LicenseViolation {
        LicenseTier tier = project.getTier();
        Instant now = Instant.now();
        
        // Check expiry
        if (project.getLicenseExpiresAt() != null && now.isAfter(project.getLicenseExpiresAt())) {
            throw new LicenseViolation("License expired on " + project.getLicenseExpiresAt());
        }
        
        // Check build quotas
        switch (tier) {
            case TRIAL:
                if (project.getBuildCount() >= 4) {
                    throw new LicenseViolation("Trial limit reached: 4 builds maximum");
                }
                break;
            case STARTER:
                if (project.getBuildCount() >= 120) {
                    throw new LicenseViolation("Starter limit reached: 120 builds maximum");
                }
                break;
            case PRO:
                if (project.getBuildCount() >= 3000) {
                    throw new LicenseViolation("Pro limit reached: 3,000 builds maximum");
                }
                break;
            case LIFETIME:
                // Unlimited with fair use check
                if (project.getBuildCount() >= 500) {
                    // Reset monthly counter if needed
                    resetMonthlyCounterIfNeeded(project);
                }
                break;
        }
    }
}
```

---

## 14. VERSION UPGRADE SYSTEM ARCHITECTURE

### 14.1 License Persistence Strategy

**License file location and format:**
```javascript
// userData/license.json - persists across app updates
{
  "licenseId": "uuid-string",
  "tier": "STARTER|PRO|LIFETIME",
  "appId": "app-uuid",
  "appVersion": "2.0.0",
  "licenseExpiresAt": "2027-03-28T10:00:00Z",
  "buildsUsed": 15,
  "maxBuilds": 120,
  "features": ["screen-protect", "offline-cache", "auto-update"],
  "issuedAt": "2026-03-28T10:00:00Z",
  "userId": "user-email@example.com"
}
```

**License migration logic:**
```javascript
// INJECT: license migration (added to main.js)
function migrateLicense(oldLicense, newVersion) {
  const migratedLicense = { ...oldLicense };
  migratedLicense.appVersion = newVersion;
  migratedLicense.lastMigratedAt = new Date().toISOString();
  
  // Preserve all license metadata across upgrades
  saveLicense(migratedLicense);
  
  return migratedLicense;
}

function checkForVersionUpgrade() {
  const currentVersion = app.getVersion();
  const license = loadLicense();
  
  if (license.appVersion !== currentVersion) {
    // Version mismatch - check for available upgrade
    fetch(`${API_BASE}/apps/${license.appId}/versions`)
      .then(response => response.json())
      .then(versions => {
        const latestVersion = versions[versions.length - 1];
        if (latestVersion.version > currentVersion) {
          showUpgradeDialog(latestVersion, license);
        } else {
          // Just migrate license data
          migrateLicense(license, currentVersion);
        }
      })
      .catch(error => {
        console.error('Failed to check for updates:', error);
        // Still migrate license even if network fails
        migrateLicense(license, currentVersion);
      });
  }
}
```

### 14.2 Version Upgrade Server Endpoints

**New endpoints in ConversionController:**
```java
@RestController
public class ConversionController {
    
    @GetMapping("/conversions/{id}/versions")
    public ResponseEntity<List<AppVersionResponse>> getAppVersions(@PathVariable String id) {
        // Return list of available versions for this app
    }
    
    @PostMapping("/conversions/{id}/upgrade/{fromVersion}/{toVersion}")
    public ResponseEntity<BuildStatusResponse> triggerVersionUpgrade(
        @PathVariable String id,
        @PathVariable String fromVersion,
        @PathVariable String toVersion) {
        // Generate upgrade installer that preserves license file
    }
    
    @GetMapping("/conversions/{id}/upgrade/{version}/download")
    public ResponseEntity<Void> downloadUpgradeInstaller(@PathVariable String id, @PathVariable String version) {
        // Redirect to R2 upgrade installer URL
    }
}
```

### 14.3 Upgrade Installer Generation

**Upgrade-specific build configuration in BuildService:**
```java
public class BuildService {
    
    public CompletableFuture<BuildResult> buildUpgrade(ConversionProject project, String targetVersion) {
        return CompletableFuture.supplyAsync(() -> {
            // Create upgrade workspace with license preservation
            Path workspace = createUpgradeWorkspace(project, targetVersion);
            
            // Add license migration script to package.json
            packageJson.put("scripts", Map.of(
                "postinstall", "node scripts/migrate-license.js"
            ));
            
            // Generate upgrade installer (patch, not full)
            return buildUpgradeInstaller(workspace, project, targetVersion);
        }, buildExecutor);
    }
}
```

**License migration script (scripts/migrate-license.js):**
```javascript
const fs = require('fs');
const path = require('path');

// Migrates license from old installation to new one
function migrateLicense() {
  const oldUserDataPath = process.env.OLD_USER_DATA_PATH;
  const newUserDataPath = require('electron').app.getPath('userData');
  
  if (oldUserDataPath && fs.existsSync(path.join(oldUserDataPath, 'license.json'))) {
    const oldLicense = JSON.parse(fs.readFileSync(path.join(oldUserDataPath, 'license.json')));
    fs.writeFileSync(path.join(newUserDataPath, 'license.json'), JSON.stringify(oldLicense, null, 2));
    console.log('License migrated successfully');
  }
}

migrateLicense();
```

---

## 16. FRONTEND INTEGRATION ARCHITECTURE

### 16.1 Enhanced Frontend Structure

**Updated Frontend Architecture for Licensing & OS-Specific Builds:**

```
frontend/src/
├── components/
│   ├── ui/                      # Reusable UI components
│   │   ├── TierBadge.tsx         # License tier display
│   │   ├── BuildProgress.tsx     # Real-time build progress
│   │   ├── OSSelector.tsx        # OS target selection
│   │   ├── ModuleToggle.tsx      # Feature module toggles
│   │   ├── LicenseExpiryBanner.tsx # Expiry warnings
│   │   └── UpgradePrompt.tsx     # Upgrade CTAs
│   ├── wizard/                  # Multi-step creation wizard
│   │   ├── ProjectWizard.tsx     # Main wizard container
│   │   ├── BasicInfoStep.tsx     # Step 1: Basic project info
│   │   ├── LicenseStep.tsx       # Step 2: Tier selection
│   │   ├── OSConfigStep.tsx      # Step 3: OS configuration
│   │   ├── FeaturesStep.tsx      # Step 4: Feature toggles
│   │   ├── ModuleConfigStep.tsx  # Step 5: Module configuration
│   │   ├── ReviewStep.tsx        # Step 6: Review & build
│   │   └── StepNavigation.tsx    # Wizard navigation
│   ├── modules/                 # Feature-specific components
│   │   ├── SplashScreenConfig.tsx
│   │   ├── DomainLockConfig.tsx
│   │   ├── WatermarkConfig.tsx
│   │   ├── KeyBindingsConfig.tsx
│   │   ├── SystemTrayConfig.tsx
│   │   ├── AutoUpdateConfig.tsx
│   │   ├── FileSystemConfig.tsx
│   │   └── WindowPolishConfig.tsx
│   ├── license/                 # License management
│   │   ├── LicenseDashboard.tsx  # License overview & usage
│   │   ├── UpgradeModal.tsx      # Upgrade flow modal
│   │   ├── TierComparison.tsx    # Tier comparison table
│   │   ├── UsageChart.tsx        # Build usage visualization
│   │   └── ExpiryScreen.tsx      # License expiry blocking screen
│   ├── build/                   # Build management
│   │   ├── BuildQueue.tsx        # Queue status & position
│   │   ├── CrossPlatformBuild.tsx # Multi-OS build interface
│   │   ├── VersionManager.tsx    # Version upgrade interface
│   │   ├── BuildMetrics.tsx      # Build statistics
│   │   └── BuildHistory.tsx      # Build history viewer
│   └── settings/                # App settings
│       ├── AutoUpgradeSettings.tsx
│       ├── NotificationSettings.tsx
│       └── AccountSettings.tsx
├── hooks/
│   ├── useLicense.ts            # License state management
│   ├── useBuildQueue.ts         # Build queue monitoring
│   ├── useFeatureConfig.ts      # Feature configuration
│   ├── useVersionUpgrade.ts     # Version upgrade logic
│   ├── useOSSelection.ts         # OS selection state
│   └── useModuleConfig.ts        # Module configuration state
├── services/
│   ├── api.ts                   # Updated base API with auth
│   ├── licenseApi.ts            # License-specific API calls
│   ├── buildApi.ts              # Build-specific API calls
│   ├── versionApi.ts            # Version management API
│   └── moduleApi.ts             # Module configuration API
├── types/
│   ├── license.ts              # License-related types
│   ├── build.ts                 # Build configuration types
│   ├── modules.ts               # Module configuration types
│   ├── upgrade.ts               # Version upgrade types
│   └── index.ts                 # Export all types
├── pages/
│   ├── DashboardPage.tsx        # Enhanced dashboard with licensing
│   ├── CreateProjectPage.tsx     # New project creation wizard
│   ├── LicensePage.tsx          # License management page
│   ├── BuildHistoryPage.tsx     # Build history and metrics
│   └── SettingsPage.tsx        # App settings
└── utils/
    ├── licenseHelpers.ts       # License utility functions
    ├── buildHelpers.ts          # Build utility functions
    └── validation.ts            # Form validation helpers
```

### 16.2 Frontend API Integration

**Enhanced API Services:**

```typescript
// licenseApi.ts - License management
export const licenseApi = {
  getCurrentLicense(): Promise<LicenseInfo>
  getLicenseDashboard(): Promise<LicenseDashboard>
  validateLicense(operation: string): Promise<LicenseValidationResponse>
  getUpgradeOptions(): Promise<UpgradeOption[]>
  initiateUpgrade(tier, billingCycle): Promise<{upgradeUrl, sessionId}>
  completeUpgrade(sessionId): Promise<LicenseInfo>
  checkFeatureAvailability(featureId): Promise<boolean>
};

// buildApi.ts - Build management
export const buildApi = {
  triggerBuild(request: BuildRequest): Promise<BuildStatusResponse>
  triggerCrossPlatformBuild(request: CrossPlatformBuildRequest): Promise<CrossPlatformBuildResult>
  getBuildStatus(projectId, targetOS?): Promise<BuildStatusResponse>
  subscribeToBuildProgress(projectId, targetOS?): EventSource
  getQueueStatus(): Promise<QueueStatus>
  getBuildMetrics(period): Promise<BuildMetrics>
  cancelBuild(projectId, targetOS?): Promise<void>
};

// versionApi.ts - Version management
export const versionApi = {
  getVersionHistory(projectId): Promise<VersionHistory>
  getAvailableUpdates(projectId): Promise<AppVersion[]>
  initiateUpgrade(request: VersionUpgradeRequest): Promise<VersionUpgradeResponse>
  getUpgradeProgress(upgradeId): Promise<UpgradeProgress>
  subscribeToUpgradeProgress(upgradeId): EventSource
  getRollbackCapability(projectId): Promise<RollbackCapability>
};
```

### 16.3 State Management Architecture

**Custom Hooks for Complex State:**

```typescript
// useLicense.ts - License state management
export function useLicense() {
  const [currentLicense, setCurrentLicense] = useState<LicenseInfo | null>(null);
  const [dashboard, setDashboard] = useState<LicenseDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  
  // Computed properties
  const isTrial = currentLicense?.tier === LicenseTier.TRIAL;
  const isExpired = currentLicense ? new Date(currentLicense.licenseExpiresAt) < new Date() : false;
  const buildsRemaining = currentLicense ? currentLicense.buildsAllowed - currentLicense.buildsUsed : 0;
  
  // Actions
  const validateLicense = async (operation: string) => { /* ... */ };
  const initiateUpgrade = async (tier, billingCycle) => { /* ... */ };
  
  return { currentLicense, dashboard, loading, isTrial, isExpired, buildsRemaining, validateLicense, initiateUpgrade };
}

// useBuildQueue.ts - Build queue monitoring
export function useBuildQueue() {
  const [activeBuilds, setActiveBuilds] = useState<Map<string, BuildStatusResponse>>(new Map());
  const [buildProgress, setBuildProgress] = useState<Map<string, BuildProgress>>(new Map());
  const [queueStatus, setQueueStatus] = useState<QueueStatus | null>(null);
  
  // SSE subscriptions for real-time updates
  const subscribeToBuildProgress = (projectId, targetOS) => { /* ... */ };
  const triggerBuild = async (projectId, targetOS, fileType, priority) => { /* ... */ };
  
  return { activeBuilds, buildProgress, queueStatus, subscribeToBuildProgress, triggerBuild };
}
```

### 16.4 Component Integration Patterns

**License-Aware Components:**

```typescript
// TierBadge.tsx - Display current license tier
export function TierBadge() {
  const { currentLicense, isExpired } = useLicense();
  
  if (!currentLicense) return null;
  
  const badgeColors = {
    TRIAL: 'bg-yellow-100 text-yellow-800',
    STARTER: 'bg-blue-100 text-blue-800',
    PRO: 'bg-purple-100 text-purple-800',
    LIFETIME: 'bg-green-100 text-green-800'
  };
  
  return (
    <div className={`px-3 py-1 rounded-full text-sm font-medium ${badgeColors[currentLicense.tier]}`}>
      {currentLicense.tier} {isExpired && '(Expired)'}
    </div>
  );
}

// FeatureToggle.tsx - Feature availability with upgrade prompts
export function FeatureToggle({ featureId, children, ...props }) {
  const { isFeatureAvailable, getUpgradeOptions } = useLicense();
  const [isAvailable, setIsAvailable] = useState(false);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  
  useEffect(() => {
    isFeatureAvailable(featureId).then(setIsAvailable);
  }, [featureId]);
  
  if (!isAvailable) {
    return (
      <div className="relative">
        <div className="opacity-50 pointer-events-none" {...props}>
          {children}
        </div>
        <button
          onClick={() => setShowUpgradeModal(true)}
          className="absolute top-2 right-2 p-1 bg-yellow-500 text-white rounded-full"
          title="Upgrade to unlock this feature"
        >
          🔒
        </button>
        {showUpgradeModal && <UpgradeModal onClose={() => setShowUpgradeModal(false)} />}
      </div>
    );
  }
  
  return <div {...props}>{children}</div>;
}
```

### 16.5 Build Queue Integration

**Real-time Build Monitoring:**

```typescript
// BuildProgress.tsx - Real-time build progress display
export function BuildProgress({ projectId, targetOS }) {
  const { buildProgress, activeBuilds, subscribeToBuildProgress } = useBuildQueue();
  const [progress, setProgress] = useState<BuildProgress | null>(null);
  
  useEffect(() => {
    const unsubscribe = subscribeToBuildProgress(projectId, targetOS);
    return unsubscribe;
  }, [projectId, targetOS]);
  
  const key = `${projectId}-${targetOS}`;
  const currentProgress = buildProgress.get(key);
  const buildStatus = activeBuilds.get(key);
  
  if (!currentProgress || !buildStatus) return null;
  
  return (
    <div className="bg-white rounded-lg shadow p-4">
      <div className="flex justify-between items-center mb-2">
        <h3 className="font-semibold">Build Progress</h3>
        <span className={`px-2 py-1 rounded text-xs ${
          buildStatus.status === 'BUILDING' ? 'bg-blue-100 text-blue-800' :
          buildStatus.status === 'READY' ? 'bg-green-100 text-green-800' :
          'bg-red-100 text-red-800'
        }`}>
          {buildStatus.status}
        </span>
      </div>
      
      <div className="w-full bg-gray-200 rounded-full h-2">
        <div 
          className="bg-blue-600 h-2 rounded-full transition-all duration-300"
          style={{ width: `${currentProgress.progress}%` }}
        />
      </div>
      
      <p className="text-sm text-gray-600 mt-2">{currentProgress.message}</p>
      {currentProgress.queuePosition && (
        <p className="text-xs text-gray-500">Queue position: {currentProgress.queuePosition}</p>
      )}
    </div>
  );
}
```
