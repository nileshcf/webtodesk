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
| **Service** | `BuildService.java` | **Week 1** | Local Git cloning, inserting generated Electron config files, running `npm install`, `npx electron-builder --win`, SSE log streaming, and uploading `.exe` to R2 |
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
| No billing/subscription gating | **P2** | Week 4 | Pending |

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

### 1.5 Build & Download Flow (R2 + GitHub Actions)

```
Frontend ──POST /build──→ Conversion Service
                              │
                              ├─ 1. Generate Electron files (config.js, main.js, preload.js, package.json)
                              ├─ 2. Push files to GitHub via Git API (blob → tree → commit → ref update)
                              ├─ 3. Dispatch GitHub Actions workflow (workflow_dispatch)
                              └─ Return 202 BUILDING
                              
Frontend ──polls /build/status every 10s──→ Conversion Service → {status, downloadUrl}

GitHub Actions (electron-build.yml) ──on completion──→
  POST /conversions/{id}/build/callback {projectId, runId, success, errorMessage}
                              │
                              ├─ Download artifact zip from GitHub Actions run
                              ├─ Extract .exe from zip
                              ├─ Upload to R2: builds/{email}/{projectId}/{filename}
                              └─ Update project: status=READY, buildArtifactPath=R2 URL

Frontend → downloads directly from R2 public URL (no Spring proxy)
```

### 1.6 Secrets & Environment Variables

> **CRITICAL:** Never hardcode secrets in code. These are documented for agent awareness only.

**conversion-service (`application.yml`):**
```yaml
server.port: ${SERVER_PORT:8082}
spring.data.mongodb.uri: ${MONGODB_URI:mongodb+srv://...}
webtodesk.r2.enabled: ${R2_ENABLED:true}
webtodesk.r2.account-id: ${R2_ACCOUNT_ID:b34ccabf01d35919541bffb8a9ad0384}
webtodesk.r2.access-key-id: ${R2_ACCESS_KEY_ID:...}
webtodesk.r2.secret-access-key: ${R2_SECRET_ACCESS_KEY:...}
webtodesk.r2.bucket: ${R2_BUCKET:webtodesk-builds}
webtodesk.r2.public-url: ${R2_PUBLIC_URL:https://pub-1b61d73b19424844a83d743c392ddea5.r2.dev}
webtodesk.r2.endpoint: https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com
build.workspace.dir: ${BUILD_WORKSPACE_DIR:../workspaces}
build.repo.url: ${BUILD_REPO_URL:https://github.com/thecheesybit/web2desk-public-release.git}
eureka.client.service-url.defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
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
│       ├── BuildService.java               # GitHub dispatch, callback, R2 upload, file gen
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
        {project_name}-setup.exe         ← Windows NSIS installer
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

### NOT YET DEPLOYED
(None - complete and functioning locally.)
```

### WEEK 2 — TEMPLATE ENGINE + MODULE SYSTEM (Days 8–14)

**Goal:** Extract Electron generation from hardcoded strings into a configurable template engine.

```
### TODO
- [ ] P0 · Add Mustache dependency to pom.xml
- [ ] P0 · Extract 4 template strings from BuildService → .mustache files
- [ ] P0 · Create TemplateEngine.java
- [ ] P0 · Refactor BuildService.generateFiles() to use TemplateEngine
- [ ] P1 · Add new fields to ConversionProject: modules, platforms, windowWidth/Height, etc.
- [ ] P1 · Create ModuleRegistry.java
- [ ] P1 · Create module templates (screen-protect, badge, offline, loader)
- [ ] P1 · OpenAPI config (springdoc-openapi)
- [ ] TEST · TemplateEngine output matches hardcoded output for zero-module builds
- [ ] TEST · ModuleRegistry validates known/unknown modules
```

### WEEK 3 — BUILD HISTORY + ASYNC PIPELINE (Days 15–21)

**Goal:** Build history tracking, build record entity, icon upload.

### WEEK 4 — PLATFORM WIRING + PRODUCTION (Days 22–30)

**Goal:** Rate limiting, subscription gating, production hardening.

---

## 9. QUALITY GATES

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
- Webhook validates caller before processing
- Failed builds set FAILED status with error — never silently drop
- Build timeout: if no callback in 30 min, mark FAILED (future)

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
| Streaming large artifacts through Spring | Use R2 redirect (302) |

---

## 11. TROUBLESHOOTING

| Problem | Fix |
|---------|-----|
| `SignatureDoesNotMatch` on R2 upload | Check `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY` — no spaces |
| `NoSuchBucket` error | Bucket must be exactly `webtodesk-builds` |
| R2 URL returns 403 | Enable public access on bucket in Cloudflare dashboard |
| GitHub Actions not triggered | Check PAT has `workflow` + `repo` scope |
| Callback not reaching Spring Boot | `CALLBACK_URL` must be publicly reachable. Use ngrok for local testing. |
| `.exe` not found in artifact zip | Check extraction logic in `BuildService.downloadGitHubArtifact()` |
| Stale Java classes | Always `mvn clean compile` before `spring-boot:run` |
| Port 8082 conflict | `Get-Process -Name java | Stop-Process -Force` |

---

*WebToDesk Conversion Service Skill — v3.1 — 2026-03-27*
*Week 1 COMPLETE (code & E2E).*
*Stack: Spring Boot 3.3.6 + MongoDB + Eureka + AWS S3 SDK + Cloudflare R2 + Local Node/Electron builds + React 19/Vite 6.*
