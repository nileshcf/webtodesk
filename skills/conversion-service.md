---
name: webtodesk-conversion-service
description: >
  Expert skill for overhauling the WebToDesk conversion-service — a Spring Boot 3.3 microservice
  (Java 17, MongoDB, Eureka) that converts website URLs into packaged Electron desktop apps.
  Part of a multi-module Maven project with api-gateway, user-service, discovery-service, and
  a React/Vite frontend. Use this skill for: upgrading the conversion-service architecture,
  adding Electron modules, improving the template engine, wiring build pipelines, integrating
  async job processing, or any task touching conversion-service code. The agent follows a 4-week
  phased overhaul plan (§8) — each week is self-contained and non-breaking.
  **Week 1 (Foundation Hardening) is COMPLETE as of 2026-03-27.** Current week: Week 2.
  Activates for: webtodesk, conversion service, electron app builder, url to desktop, desktop
  wrapper, electron template, build pipeline, conversion project.
stack: Java 17, Spring Boot 3.3.6, Spring Cloud 2023.0.4, MongoDB, Eureka, Lombok,
       React 19/Vite 6, Electron 38+, electron-builder 26+, Docker Compose, GitHub Actions
principles: microservices, service-discovery, backwards-compatibility, incremental-delivery,
            zero-downtime, test-before-refactor, modular-templates, fail-safe-defaults
---

# WebToDesk Conversion Service — Overhaul Skill

> This skill gives an AI agent complete context on the **actual** codebase, a gap analysis against
> the target vision, and a safe 4-week phased plan to upgrade the conversion-service from its
> current MVP state to a production-grade Electron app factory — without breaking anything.

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
| **Entity** | `ConversionProject.java` | MVP | 10 fields, `ConversionStatus` enum (DRAFT/READY/BUILDING/FAILED), MongoDB `@Document` |
| **Repository** | `ConversionRepository.java` | MVP | `MongoRepository`, single custom query `findByCreatedByOrderByCreatedAtDesc` |
| **Service** | `ConversionService.java` | **Hardened** | CRUD + `generateElectronProject()` with 4 hardcoded Java template strings. Now uses `ProjectNotFoundException` (not raw RuntimeException). |
| **Controller** | `ConversionController.java` | **Hardened** | 6 endpoints + explicit `@PathVariable("id")` + `@Valid` on update |
| **Health** | `HealthController.java` | **Week 1** | `GET /conversions/health` → `{"status":"UP","service":"conversion-service"}` |
| **DTOs** | 5 records | **Hardened** | + `ErrorResponse.java`. `CreateConversionRequest` and `UpdateConversionRequest` have rich validation. |
| **Exceptions** | `exception/` package | **Week 1** | `ProjectNotFoundException`, `GlobalExceptionHandler` (`@RestControllerAdvice`) |
| **Security** | `ConversionSecurityConfig.java` | Stub | Permits all requests (relies on API gateway auth) |
| **Config** | `MongoConfig.java` | **Week 1** | `@EnableMongoAuditing` extracted from SecurityConfig |
| **Config** | `application.yml` | **Hardened** | Port 8082, MongoDB via `${MONGODB_URI}`, Eureka via `${EUREKA_URL}` |
| **Config** | `application-dev.yml` | **Week 1** | Local dev profile |
| **Config** | `application-test.yml` | **Week 1** | Test profile |
| **Tests** | 28 tests | **Week 1** | 18 service + 10 controller unit tests, all passing |

### 1.2 What's Missing (vs. Target Vision)

| Gap | Priority | Target Week | Status |
|-----|----------|-------------|--------|
| ~~No global exception handler~~ | **P0** | Week 1 | **DONE** |
| ~~No input validation beyond `@NotBlank` / `@URL` on create~~ | **P0** | Week 1 | **DONE** |
| ~~No unit or integration tests~~ | **P0** | Week 1 | **DONE** (28 tests) |
| ~~MongoDB URI hardcoded in `application.yml`~~ | **P0** | Week 1 | **DONE** |
| ~~No health check endpoint~~ | **P0** | Week 1 | **DONE** |
| Electron templates are 4 hardcoded strings in `ConversionService` | **P1** | Week 2 | Pending |
| No module system — no configurable modules (screen-protect, biometric, etc.) | **P1** | Week 2 | Pending |
| No API documentation (OpenAPI) | **P1** | Week 2 | Pending |
| No async build pipeline — `generateElectronProject` is synchronous | **P1** | Week 3 | Pending |
| No build status tracking / real-time updates | **P1** | Week 3 | Pending |
| No file storage (icon upload, artifact storage, S3/R2) | **P1** | Week 3 | Pending |
| No build queue / job worker | **P1** | Week 3 | Pending |
| No billing/subscription gating | **P2** | Week 4 | Pending |
| No CI/CD workflow for Electron builds | **P2** | Week 4 | Pending |
| No SDK / webhook integration | **P2** | Week 4 | Pending |

### 1.3 Sibling Services (context only — do not modify without explicit request)

| Service | Port | Stack | Purpose |
|---------|------|-------|---------|
| `discovery-service` | 8761 | Spring Boot + Eureka Server | Service registry. Self-registers disabled. Must start first. |
| `api-gateway` | 8080 | Spring Cloud Gateway (reactive/WebFlux) | JWT validation, route mapping, header injection. Routes `/conversion/**` → `lb://conversion-service` with `StripPrefix=1`. |
| `user-service` | 8081 | Spring Boot + PostgreSQL (Neon) + Redis (Upstash) | User CRUD, auth (login/register/refresh/logout), profile management. Uses `spring.profiles.active=dev`. |
| `frontend` | 5173 | React 19 + Vite 6 + TailwindCSS 3.4 + Framer Motion + Lucide | SPA dashboard. Vite proxies `/user` and `/conversion` to gateway at `:8080`. |
| `common` | — | Maven JAR module | Shared `JwtTokenProvider`, `JwtValidator`, `JwtConstants`, `ErrorResponse` DTO. Used by api-gateway and user-service. |

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

**Auth flow detail:**
1. Frontend calls `POST /user/auth/login` → user-service returns `{ accessToken, refreshToken, expiresIn }`
2. Frontend stores tokens in `localStorage`, attaches `Authorization: Bearer {token}` to all requests
3. API Gateway's `SecurityContextRepository` extracts Bearer token from `Authorization` header
4. `AuthenticationManager` validates JWT using `JwtValidator` from `common` module
5. `HeaderForwardingFilter` (runs at order `-1`, before routing) extracts claims and injects:
   - `X-User-Id` (from `userId` claim)
   - `X-User-Email` (from JWT subject)
   - `X-User-Roles` (from `roles` claim)
6. conversion-service reads `X-User-Email` header in `@RequestHeader("X-User-Email")` — no JWT parsing needed
7. conversion-service permits all requests locally (`ConversionSecurityConfig` → `anyRequest().permitAll()`)

**Open endpoints (no auth required):** `/user/auth/login`, `/user/auth/register`, `/user/auth/refresh`, `/actuator/health`

### 1.5 Secrets & Environment Variables (full project)

> **CRITICAL:** Never hardcode secrets in code. These are documented for agent awareness only.

**JWT Secrets (shared between api-gateway and user-service):**
- `jwt.access-secret` — HMAC key for access tokens (currently hardcoded in `application.yml` of both services)
- `jwt.refresh-secret` — HMAC key for refresh tokens
- `jwt.access-token-expiry` — 900000ms (15 min)
- `jwt.refresh-token-expiry` — 2592000000ms (30 days)

**user-service (dev profile):**
- PostgreSQL: Neon cloud (`jdbc:postgresql://ep-wispy-grass-...neon.tech/neondb`)
- Redis: Upstash (`rediss://default:...@alive-titmouse-22739.upstash.io:6379`)
- Credentials in `application-dev.yml` (not env vars yet — security debt)

**conversion-service (externalized in Week 1):**
- `MONGODB_URI` — MongoDB Atlas connection string (default in `application.yml`)
- `EUREKA_URL` — Eureka registry URL (default: `http://localhost:8761/eureka/`)
- `SERVER_PORT` — HTTP port (default: `8082`)

**Gateway CORS:**
- Allowed origins: `http://localhost:3000`, `http://localhost:5173`
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- Credentials: true

### 1.6 Frontend Integration Details

**Frontend stack:** React 19, Vite 6, TypeScript 5.7, TailwindCSS 3.4, Framer Motion 11, Lucide React, Axios

**Conversion API client** (`frontend/src/services/api.ts`):
- Base URL: `/` (all requests proxied through Vite → API Gateway)
- Token: `localStorage.getItem('accessToken')` attached via Axios interceptor
- Auto-refresh: schedules token refresh 60s before expiry

**Frontend types** (`frontend/src/types/index.ts`):
```typescript
interface ConversionProject {
  id: string; projectName: string; websiteUrl: string; appTitle: string;
  iconFile: string; currentVersion: string;
  status: 'DRAFT' | 'READY' | 'BUILDING' | 'FAILED';
  createdBy: string; createdAt: string; updatedAt: string;
}
interface CreateConversionRequest {
  projectName: string; websiteUrl: string; appTitle: string; iconFile?: string;
}
interface ElectronConfig {
  projectName: string; appTitle: string; websiteUrl: string;
  files: Record<string, string>;
}
```

**Frontend API calls** (must match backend contract exactly):
- `conversionApi.list()` → `GET /conversion/conversions` (note: `/conversion` prefix for gateway route)
- `conversionApi.create(data)` → `POST /conversion/conversions`
- `conversionApi.getById(id)` → `GET /conversion/conversions/${id}`
- `conversionApi.update(id, data)` → `PUT /conversion/conversions/${id}`
- `conversionApi.remove(id)` → `DELETE /conversion/conversions/${id}`
- `conversionApi.generate(id)` → `POST /conversion/conversions/${id}/generate`

**Dashboard behavior** (`DashboardPage.tsx`):
- On mount: calls `conversionApi.list()` to load projects
- Create form: projectName, websiteUrl (required `https://`), appTitle, iconFile (optional)
- Error display: reads `err.response?.data?.message` — **matches our `ErrorResponse.message` field**
- Generate: opens modal showing file contents with download buttons per file + "Download All"
- Status badges: DRAFT (grey), READY (green), BUILDING (orange), FAILED (red)

### 1.7 Build & Startup

**Maven multi-module** (parent POM at root):
- Java 17, Spring Boot 3.3.6, Spring Cloud 2023.0.4
- Modules: `common`, `user-service`, `api-gateway`, `discovery-service`, `conversion-service`
- `common` module provides shared JWT utilities (NOT used by conversion-service)

**Local startup** (`start-all.ps1`):
1. Sets `JAVA_HOME=C:\Program Files\Java\jdk-17`
2. Starts discovery-service, waits for Eureka at `:8761` (60s timeout)
3. Starts user-service, conversion-service, api-gateway (each with 5s delay)
4. Starts frontend via `npm run dev`

**Maven wrapper:** Root `mvnw.cmd` shared by all modules. Each service runs via `cd service-dir && ..\mvnw.cmd spring-boot:run`. conversion-service also has its own local `mvnw.cmd` for standalone builds.

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

Rules:
- Each TODO item is **atomic** (one file, one function, one config block).
- P0 items are completed before P1 begins.
- Every code TODO has a paired TEST TODO.
- No TODO item is skipped — mark `[x]` when done, `[!]` if blocked.
- **Never bundle Week 3 work into a Week 1 task.** Stay in scope.

---

## 3. PROJECT STRUCTURE — CURRENT vs. TARGET

### 3.1 Current Layout (`conversion-service/`) — post-Week 1

```
conversion-service/
├── pom.xml                          # Spring Boot 3.3 + MongoDB + Security + Eureka + Validation
├── mvnw / mvnw.cmd                  # Week 1: standalone Maven wrapper
├── .mvn/wrapper/                    # Week 1: Maven wrapper config
├── Dockerfile                       # Multi-stage Docker build
├── src/main/java/com/example/conversion_service/
│   ├── ConversionServiceApplication.java
│   ├── config/
│   │   ├── ConversionSecurityConfig.java   # Stub: permitAll (gateway handles auth)
│   │   └── MongoConfig.java                # Week 1: @EnableMongoAuditing
│   ├── controller/
│   │   ├── ConversionController.java       # Week 1: explicit @PathVariable("id"), @Valid on update
│   │   └── HealthController.java           # Week 1: GET /conversions/health
│   ├── dto/
│   │   ├── CreateConversionRequest.java    # Week 1: rich validation (@Size, @Pattern, @NotBlank)
│   │   ├── UpdateConversionRequest.java    # Week 1: conditional validation
│   │   ├── ConversionResponse.java
│   │   ├── ElectronConfigResponse.java
│   │   └── ErrorResponse.java              # Week 1: structured error DTO
│   ├── entity/
│   │   └── ConversionProject.java
│   ├── exception/                          # Week 1: entire package
│   │   ├── GlobalExceptionHandler.java     # @RestControllerAdvice (5 handlers)
│   │   └── ProjectNotFoundException.java   # Typed runtime exception
│   ├── repository/
│   │   └── ConversionRepository.java
│   └── service/
│       └── ConversionService.java          # Week 1: uses ProjectNotFoundException
├── src/main/resources/
│   ├── application.yml                     # Week 1: externalized env vars
│   ├── application-dev.yml                 # Week 1: local dev profile
│   └── application-test.yml               # Week 1: test profile
└── src/test/java/com/example/conversion_service/
    ├── controller/
    │   └── ConversionControllerTest.java   # Week 1: 10 unit tests (direct invocation + Mockito)
    └── service/
        └── ConversionServiceTest.java      # Week 1: 18 unit tests (@MockBean repository)
```

### 3.2 Target Layout (end of Week 4) — additive only, never delete existing files

```
conversion-service/
├── pom.xml                          # + springdoc-openapi, spring-kafka/AMQP (Week 3), testcontainers
├── src/main/java/com/example/conversion_service/
│   ├── ConversionServiceApplication.java
│   ├── config/
│   │   ├── ConversionSecurityConfig.java
│   │   ├── MongoConfig.java                    # Week 1: auditing + indexes
│   │   └── OpenApiConfig.java                  # Week 2: Swagger/OpenAPI
│   ├── controller/
│   │   ├── ConversionController.java           # extended, not replaced
│   │   └── HealthController.java               # Week 1: /health endpoint
│   ├── dto/
│   │   ├── CreateConversionRequest.java        # Week 1: richer validation
│   │   ├── UpdateConversionRequest.java
│   │   ├── ConversionResponse.java
│   │   ├── ElectronConfigResponse.java
│   │   ├── BuildRequest.java                   # Week 3: async build trigger
│   │   ├── BuildStatusResponse.java            # Week 3: status polling
│   │   └── ErrorResponse.java                  # Week 1: structured errors
│   ├── entity/
│   │   ├── ConversionProject.java              # Week 2: + modules[], platforms[], windowConfig
│   │   └── BuildRecord.java                    # Week 3: build history entity
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java         # Week 1
│   │   ├── ProjectNotFoundException.java       # Week 1
│   │   └── BuildLimitExceededException.java    # Week 4
│   ├── repository/
│   │   ├── ConversionRepository.java
│   │   └── BuildRecordRepository.java          # Week 3
│   ├── service/
│   │   ├── ConversionService.java              # refactored, not rewritten
│   │   ├── TemplateEngine.java                 # Week 2: extracted from ConversionService
│   │   ├── ModuleRegistry.java                 # Week 2: configurable module system
│   │   ├── BuildOrchestrator.java              # Week 3: async build dispatch
│   │   └── StorageService.java                 # Week 3: icon upload + artifact storage
│   └── template/                               # Week 2: externalised Electron templates
│       ├── main.js.mustache
│       ├── preload.js.mustache
│       ├── package.json.mustache
│       ├── config.js.mustache
│       └── modules/                            # Week 2: per-module templates
│           ├── screen-protect.js.mustache
│           ├── biometric.js.mustache
│           ├── badge.js.mustache
│           ├── offline.js.mustache
│           ├── deeplink.js.mustache
│           └── loader.js.mustache
├── src/main/resources/
│   ├── application.yml                         # Week 1: externalise secrets via env vars
│   └── application-dev.yml                     # Week 1: local dev profile
├── src/test/java/com/example/conversion_service/
│   ├── controller/
│   │   └── ConversionControllerTest.java       # Week 1
│   ├── service/
│   │   ├── ConversionServiceTest.java          # Week 1
│   │   └── TemplateEngineTest.java             # Week 2
│   └── integration/
│       └── ConversionFlowIntegrationTest.java  # Week 1 (Testcontainers + MongoDB)
└── Dockerfile                                  # exists, improved Week 3

---

## 4. EXISTING API CONTRACT (current — do not break)

### Authentication
Requests arrive via `api-gateway` which attaches `X-User-Email` header after JWT validation.
The conversion-service itself permits all requests (`ConversionSecurityConfig` → `anyRequest().permitAll()`).

### Current Endpoints (`/conversions` base path)

```
POST   /conversions                   Create conversion project
GET    /conversions                   List user's projects (via X-User-Email header)
GET    /conversions/{id}              Get project by ID
PUT    /conversions/{id}              Update project
DELETE /conversions/{id}              Delete project
POST   /conversions/{id}/generate     Generate Electron project files (synchronous)
```

### Current Request/Response Shapes

**CreateConversionRequest:**
```json
{
  "projectName": "string, @NotBlank",
  "websiteUrl":  "string, @NotBlank, @URL",
  "appTitle":    "string, @NotBlank",
  "iconFile":    "string, optional, defaults to 'icon.ico'"
}
```

**ConversionResponse:**
```json
{
  "id": "string (MongoDB ObjectId)",
  "projectName": "string (sanitised, lowercase, hyphens)",
  "websiteUrl": "string",
  "appTitle": "string",
  "iconFile": "string",
  "currentVersion": "string (default '1.0.0')",
  "status": "DRAFT | READY | BUILDING | FAILED",
  "createdBy": "string (email)",
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

**ElectronConfigResponse (from /generate):**
```json
{
  "projectName": "string",
  "appTitle": "string",
  "websiteUrl": "string",
  "files": {
    "config.js": "string (file content)",
    "main.js": "string (file content)",
    "preload.js": "string (file content)",
    "package.json": "string (file content)"
  }
}
```

### Target Endpoints (added incrementally — never remove existing ones)

```
# Week 1 additions
GET    /conversions/health            Health check (no auth)

# Week 2 additions (extend /generate response, don't change URL)
POST   /conversions/{id}/generate     Now returns modules + richer templates

# Week 3 additions
POST   /conversions/{id}/builds       Trigger async build (new endpoint)
GET    /conversions/{id}/builds       Build history (new endpoint)
GET    /conversions/builds/{buildId}  Build status (new endpoint)

# Week 4 additions
POST   /conversions/webhooks/build-complete   GitHub Actions callback
GET    /conversions/{id}/download/{buildId}   Pre-signed download URL
```

---

## 5. ELECTRON MODULE CONTRACT (target — implemented in Week 2 templates)

Every module generated by the template engine MUST implement this interface:

```js
// modules/<name>/<name>.js
/**
 * MODULE CONTRACT
 * @param {Electron.BrowserWindow} mainWindow
 * @param {object} config  — full user-config.json
 * @returns {void}
 */
function setup(mainWindow, config) {
  if (!config.modules?.includes('<MODULE_NAME>')) return
  // Implementation here
}

/**
 * Teardown — called on app.before-quit
 */
function teardown() {}

module.exports = { setup, teardown }
```

### Module Registry (generated by `ModuleRegistry.java` in Week 2)

| Module Key | Tier | File | Description |
|-----------|------|------|-------------|
| `screen-protect` | Pro | `security/screen-protect.js` | `setContentProtection(true)` — blocks screenshots/recording |
| `biometric` | Pro | `auth/biometric.js` | Touch ID / Windows Hello lock |
| `push-fcm` | Pro | `notifications/fcm-client.js` | Firebase Cloud Messaging desktop push |
| `deep-link` | Pro | `deeplink/deeplink.js` | Custom protocol `myapp://` registration |
| `offline` | Free | `offline/offline.js` | Offline detection + custom error page |
| `analytics` | Pro | `analytics/analytics.js` | Firebase Analytics bridge |
| `sidebar` | Free | `sidebar/sidebar.js` | Injected navigation sidebar |
| `badge` | Free | `badge/badge.js` | `app.setBadgeCount(n)` via IPC |
| `auth-lock` | Pro | `auth/auth-lock.js` | Lock screen on launch/resume |
| `dlp` | Pro | `security/dlp.js` | Disable right-click save/copy |

### Dynamic Loader (generated template — `loader.js.mustache`)

```js
const path = require('path')
const MODULE_REGISTRY = {
{{#modules}}
  '{{key}}': './{{path}}',
{{/modules}}
}

function setupModules(mainWindow, config) {
  const enabled = config.modules || []
  const teardowns = []
  for (const key of enabled) {
    const modPath = MODULE_REGISTRY[key]
    if (!modPath) { console.warn(`Unknown module: ${key}`); continue }
    const mod = require(path.join(__dirname, modPath))
    mod.setup(mainWindow, config)
    if (mod.teardown) teardowns.push(mod.teardown)
  }
  require('electron').app.on('before-quit', () => teardowns.forEach(fn => fn()))
}

module.exports = { setupModules }
```

---

## 6. ELECTRON TEMPLATE — CURRENT GENERATED OUTPUT

The current `ConversionService.generateElectronProject()` produces 4 files via hardcoded Java
text blocks. These are the **baseline** — Week 2 extracts them into Mustache templates with
module support, but the output contract remains identical for zero-module builds.

### Current Generated `main.js` Features
- `BrowserWindow` with `nodeIntegration: false`, `contextIsolation: true`
- `setContentProtection(true)` hardcoded (should be module-controlled)
- Screenshot shortcut interception (global shortcuts for PrintScreen, Cmd+Shift+3/4/5)
- DevTools auto-close
- Menu bar hidden
- `open-external` IPC handler

### Current Generated `preload.js` Features
- `contextBridge.exposeInMainWorld('electronAPI', { openExternal })` — minimal bridge
- Screenshot protection UI overlay (blackout + countdown timer + red border flash)

### What's Missing in Generated Output (addressed in Weeks 2-3)
- No `modules/loader.js` — all features hardcoded
- No `menu.js`, `tray.js`, `updater.js` — no native menu, tray, or auto-update
- No `user-config.json` — config baked into `config.js` only
- Window size/minSize not configurable (hardcoded 1200×800)
- No splash screen
- No offline fallback page
- No module enable/disable — screen protection is always on

---

## 7. REFERENCE PATTERNS (from target vision doc)

These patterns from `docs/conversion_service-refrencer.md` guide the overhaul but are
implemented incrementally. **Do not copy-paste these wholesale** — adapt to Spring Boot.

### 7.1 Build Pipeline Phases (target — Week 3)
1. **Config Validation** → Service validates request, creates `BuildRecord` in MongoDB
2. **Template Cloning** → Copy master template, inject user config via `TemplateEngine`
3. **Dependency Install** → `npm ci --production` (done by CI/CD agent, not Java service)
4. **Compilation** → `electron-builder --{platform}` (CI/CD agent)
5. **Code Signing** → Apple/Windows certs (CI/CD agent)
6. **Upload & Delivery** → Artifact → S3/R2, status → MongoDB (webhook callback)

### 7.2 Target `ConversionProject` Entity Fields (Week 2 expansion)
```java
// Existing fields (do not remove):
String id, projectName, websiteUrl, appTitle, iconFile, currentVersion, createdBy
ConversionStatus status
Instant createdAt, updatedAt

// Week 2 additions (all nullable with defaults — backwards compatible):
List<String> modules           // default: empty list
List<String> platforms         // default: ["win"]
Integer windowWidth            // default: 1280
Integer windowHeight           // default: 800
Integer minWidth               // default: 800
Integer minHeight              // default: 600
String customCSS               // default: null
String customJS                // default: null
String splashColor             // default: null
String appId                   // default: "com.{projectName}.app"
```

### 7.3 Target `BuildRecord` Entity (Week 3 — new collection)
```java
@Document(collection = "builds")
public class BuildRecord {
    @Id String id;
    String projectId;           // references ConversionProject.id
    String platform;            // "mac" | "win" | "linux"
    BuildStatus status;         // QUEUED, BUILDING, DONE, FAILED
    Integer progress;           // 0-100
    String phase;               // "validating" | "cloning" | "injecting" | "compiling" | "uploading"
    Map<String, String> urls;   // {"dmg": "https://...", "exe": "https://..."}
    Long artifactSizeBytes;
    String errorMessage;
    String triggeredBy;         // user email
    @CreatedDate Instant createdAt;
    Instant completedAt;
}
```

### 7.4 Security Rules (always enforced)
- `nodeIntegration: false` in all generated `BrowserWindow` instances
- `contextIsolation: true` in all generated `BrowserWindow` instances
- `preload` path uses `path.join(__dirname, ...)` — never string concatenation
- No `eval()` anywhere in generated main process code
- All IPC channels explicitly registered — no wildcard listeners
- `webSecurity: true` — never disabled in generated output
- No credentials in generated source code

---

## 8. FOUR-WEEK OVERHAUL PLAN

> **Guiding principle:** Each week is a self-contained deliverable. At the end of every week,
> the service compiles, all tests pass, all existing endpoints work identically, and Docker
> Compose boots cleanly. No week depends on the next being completed.

---

### WEEK 1 — FOUNDATION HARDENING (Days 1–7) ✅ COMPLETE (2026-03-27)

**Goal:** Make the existing service production-worthy without changing any business logic.

```
## TASK: Foundation hardening — error handling, config, validation, tests, health check
## WEEK: 1
## SCOPE: exception|config|controller|dto|test
## STATUS: ✅ COMPLETE — 28/28 tests passing, all endpoints verified live

### TODO
- [x] P0 · Create `exception/ProjectNotFoundException.java` (extends RuntimeException)
- [x] P0 · Create `exception/GlobalExceptionHandler.java` (@RestControllerAdvice)
         — handle ProjectNotFoundException → 404 + ErrorResponse
         — handle MethodArgumentNotValidException → 400 + field errors
         — handle MissingRequestHeaderException → 400 (added for X-User-Email)
         — handle IllegalArgumentException → 400 (added for safety)
         — handle generic Exception → 500 + safe message (no stack trace)
         NOTE: Uses WebRequest (not HttpServletRequest) for MockMvc compatibility
- [x] P0 · Create `dto/ErrorResponse.java` record: { message, errors, timestamp, path }
- [x] P0 · Replace `throw new RuntimeException(...)` in ConversionService with ProjectNotFoundException
- [x] P0 · Externalise MongoDB URI in application.yml: `${MONGODB_URI:...atlas-default...}`
- [x] P0 · Create `application-dev.yml` with local MongoDB fallback
- [x] P0 · Create `application-test.yml` with test profile
- [x] P0 · Create `controller/HealthController.java` — GET /conversions/health → {"status":"UP"}
- [x] P1 · Add richer validation to CreateConversionRequest:
         — websiteUrl: @Pattern must start with https://
         — projectName: @Size(max=64), @Pattern [a-zA-Z0-9 _-]+
         — appTitle: @Size(max=128)
- [x] P1 · Add @Pattern validation to UpdateConversionRequest fields (nullable but validated if present)
         — currentVersion: @Pattern semver X.Y.Z
- [x] P1 · Create `config/MongoConfig.java` — @EnableMongoAuditing (moved from SecurityConfig)
- [x] P1 · Add explicit @PathVariable("id") to all path variable endpoints
         — CRITICAL BUG FIX: Without this, Java compiler doesn't preserve param names → 500 error
- [x] P1 · Add @Valid to UpdateConversionRequest on PUT endpoint
- [x] TEST · ConversionControllerTest — 10 unit tests (direct method invocation + Mockito)
         NOTE: MockMvc standalone was abandoned due to path variable resolution issues;
         direct invocation tests controller logic, live testing covers HTTP layer
- [x] TEST · ConversionServiceTest — 18 unit tests with @Mock repository
- [!] TEST · Integration test with embedded MongoDB — SKIPPED (de.flapdoodle not compatible,
         Testcontainers requires Docker; covered by live endpoint testing instead)
- [x] DOCS · application.yml uses ${MONGODB_URI}, ${EUREKA_URL}, ${SERVER_PORT} env vars
```

**Verification command:** `mvn clean test -pl conversion-service` → 28/28 PASS

**Live endpoint verification (all passing):**
- POST /conversions → 200 (create)
- GET /conversions → 200 (list by user)
- GET /conversions/{id} → 200 (get by ID)
- PUT /conversions/{id} → 200 (update)
- DELETE /conversions/{id} → 204 (delete)
- POST /conversions/{id}/generate → 200 (Electron files)
- GET /conversions/health → 200 (health check)
- GET /conversions/{nonexistent} → 404 (structured error)
- POST /conversions with http:// URL → 400 (validation error with field map)

**Non-breaking guarantee:** All existing endpoints behave identically. New exception handler only
changes error *format* (structured JSON instead of Spring's default), which is an improvement.
Health endpoint is additive.

---

### WEEK 2 — TEMPLATE ENGINE + MODULE SYSTEM (Days 8–14)

**Goal:** Extract Electron generation from hardcoded strings into a configurable template engine
with module support. The `/generate` endpoint returns richer output but stays backward-compatible.

```
## TASK: Template engine extraction + module system
## WEEK: 2
## SCOPE: service|entity|template|config|test

### TODO
- [ ] P0 · Add Mustache dependency to pom.xml (com.github.spullara.mustache.java:compiler)
- [ ] P0 · Create `src/main/resources/templates/electron/` directory
- [ ] P0 · Extract current main.js generation → `templates/electron/main.js.mustache`
- [ ] P0 · Extract current preload.js generation → `templates/electron/preload.js.mustache`
- [ ] P0 · Extract current config.js generation → `templates/electron/config.js.mustache`
- [ ] P0 · Extract current package.json generation → `templates/electron/package.json.mustache`
- [ ] P0 · Create `service/TemplateEngine.java`:
         — compile(templateName, contextMap) → String
         — generateProject(ConversionProject) → Map<String, String>
         — replaces the 4 private generate*() methods in ConversionService
- [ ] P0 · Refactor ConversionService.generateElectronProject() to delegate to TemplateEngine
         — MUST produce identical output for existing projects (no modules, no new fields)
- [ ] P1 · Add new fields to ConversionProject entity (all nullable, backward compatible):
         — modules (List<String>), platforms (List<String>)
         — windowWidth, windowHeight, minWidth, minHeight (Integer)
         — customCSS, customJS, splashColor, appId (String)
- [ ] P1 · Update CreateConversionRequest + UpdateConversionRequest with new optional fields
- [ ] P1 · Update ConversionResponse to include new fields
- [ ] P1 · Create `service/ModuleRegistry.java`:
         — AVAILABLE_MODULES map: key → { tier, templatePath, description }
         — validate(List<String> requestedModules) → throws if invalid module key
         — getModulesForTier(String tier) → list of allowed modules
- [ ] P1 · Create module templates under `templates/electron/modules/`:
         — loader.js.mustache (dynamic loader, driven by modules list)
         — screen-protect.js.mustache, badge.js.mustache, offline.js.mustache
         — (remaining modules added in future weeks — non-blocking)
- [ ] P1 · Update TemplateEngine to conditionally include module files in output
- [ ] P1 · Create `config/OpenApiConfig.java` + add springdoc-openapi-starter-webmvc-ui to pom.xml
- [ ] TEST · TemplateEngineTest — verify output matches current hardcoded output for legacy projects
- [ ] TEST · TemplateEngineTest — verify module templates are included when modules[] is set
- [ ] TEST · ModuleRegistryTest — validate known modules pass, unknown modules fail
- [ ] TEST · ConversionControllerTest — verify /generate returns module files when configured
- [ ] DOCS · Update ElectronConfigResponse Javadoc with new file entries
```

**Verification command:** `mvn clean test -pl conversion-service`

**Non-breaking guarantee:** The key invariant is that `TemplateEngine.generateProject()` produces
**byte-identical** output to the old hardcoded methods when a project has no modules and no new
fields. Test this explicitly. New fields are nullable with defaults — existing MongoDB documents
remain valid.

---

### WEEK 3 — ASYNC BUILD PIPELINE + STATUS TRACKING (Days 15–21)

**Goal:** Add async build dispatch, build history tracking, and artifact storage integration.
The synchronous `/generate` endpoint continues to work — the new `/builds` endpoints are additive.

```
## TASK: Async build pipeline with status tracking
## WEEK: 3
## SCOPE: entity|repository|service|controller|config|test

### TODO
- [ ] P0 · Create `entity/BuildRecord.java` — new MongoDB collection "builds"
         — fields: id, projectId, platform, status, progress, phase, urls, artifactSize,
           errorMessage, triggeredBy, createdAt, completedAt
         — BuildStatus enum: QUEUED, BUILDING, DONE, FAILED
- [ ] P0 · Create `repository/BuildRecordRepository.java`
         — findByProjectIdOrderByCreatedAtDesc(String projectId)
         — findByStatus(BuildStatus status) — for worker polling
- [ ] P0 · Create `dto/BuildRequest.java` — record(List<String> platforms)
- [ ] P0 · Create `dto/BuildStatusResponse.java` — record mapped from BuildRecord
- [ ] P0 · Create `service/BuildOrchestrator.java`:
         — triggerBuild(String projectId, BuildRequest, String userEmail) → BuildStatusResponse
           1. Validate project exists + has status READY
           2. Create BuildRecord per platform with status QUEUED
           3. (Phase 1: just persist — actual dispatch to GitHub Actions is Week 4)
           4. Return build IDs
         — getBuildStatus(String buildId) → BuildStatusResponse
         — getBuildHistory(String projectId) → List<BuildStatusResponse>
         — updateBuildStatus(String buildId, status, progress, phase, urls, error)
           — called by webhook callback (Week 4) or manually for testing
- [ ] P0 · Add build endpoints to ConversionController:
         — POST /{id}/builds → triggerBuild
         — GET /{id}/builds → getBuildHistory
         — GET /builds/{buildId} → getBuildStatus
- [ ] P1 · Create `service/StorageService.java` (interface + local filesystem impl):
         — uploadIcon(String projectId, byte[] iconData) → String (storage key)
         — getDownloadUrl(String buildId, String platform) → String
         — (S3/R2 implementation deferred to Week 4 — local filesystem for now)
- [ ] P1 · Add icon upload endpoint: POST /conversions/{id}/icon (multipart)
- [ ] P1 · Update ConversionService.generateElectronProject() to also set status to READY
         (already does this — verify it works with new build flow)
- [ ] P1 · Add idempotency check: if a QUEUED/BUILDING build exists for same project+platform,
         reject duplicate trigger with 409 Conflict
- [ ] P2 · Improve Dockerfile: multi-stage build, non-root user, health check
- [ ] TEST · BuildOrchestratorTest — unit tests for trigger, status, history, idempotency
- [ ] TEST · Integration test — full flow: create project → generate → trigger build → check status
- [ ] TEST · StorageService local impl tests
- [ ] DOCS · Document new /builds endpoints in OpenAPI annotations
```

**Verification command:** `mvn clean test -pl conversion-service && docker compose build conversion-service`

**Non-breaking guarantee:** Existing `/generate` endpoint is untouched. New `/builds` endpoints
are purely additive. `BuildRecord` is a new collection — no existing data affected. Build
dispatch is a no-op (creates QUEUED records) until Week 4 wires GitHub Actions.

---

### WEEK 4 — PLATFORM WIRING + PRODUCTION READINESS (Days 22–30)

**Goal:** Wire the build pipeline to GitHub Actions, add webhook callbacks, billing hooks,
rate limiting, and production hardening. This is the "connect everything" week.

```
## TASK: GitHub Actions integration, webhooks, rate limiting, production polish
## WEEK: 4
## SCOPE: service|controller|config|pipeline|test

### TODO
- [ ] P0 · Create `service/GitHubActionsClient.java`:
         — dispatchBuild(BuildRecord, ConversionProject) → triggers workflow_dispatch
         — uses GitHub REST API: POST /repos/{owner}/{repo}/actions/workflows/{id}/dispatches
         — sends build_id + base64-encoded project config as inputs
         — requires GITHUB_TOKEN env var (PAT with workflow scope)
- [ ] P0 · Wire BuildOrchestrator.triggerBuild() to call GitHubActionsClient
- [ ] P0 · Create webhook endpoint: POST /conversions/webhooks/build-complete
         — accepts { buildId, status, urls, artifactSize, error } from GitHub Actions
         — validates HMAC signature header (shared secret)
         — calls BuildOrchestrator.updateBuildStatus()
- [ ] P0 · Create `.github/workflows/build-electron.yml`:
         — workflow_dispatch with inputs: build_id, user_config (base64), platform
         — matrix: [macos-latest (mac), ubuntu-latest (win/linux)]
         — steps: checkout, setup-node, inject config, npm ci, electron-builder, upload S3
         — final step: POST webhook to conversion-service with build result
- [ ] P1 · Add rate limiting: max 10 build triggers per user per hour
         — Create `exception/BuildLimitExceededException.java`
         — Simple in-memory counter (or Redis if available) — check in BuildOrchestrator
         — Handle in GlobalExceptionHandler → 429 Too Many Requests
- [ ] P1 · Add download endpoint: GET /conversions/{id}/download/{buildId}
         — returns pre-signed URL from StorageService (or redirect)
         — validates user owns the project
- [ ] P1 · Create S3/R2 implementation of StorageService (behind Spring profile):
         — `StorageServiceS3Impl` activated by `spring.profiles.active=prod`
         — `StorageServiceLocalImpl` remains default for dev
- [ ] P2 · Add subscription tier check stub in BuildOrchestrator:
         — interface `SubscriptionChecker` with method `getTier(String userEmail) → FREE|PRO`
         — default impl returns PRO for all users (gating wired when billing is ready)
         — ModuleRegistry.validate() checks module tier against user tier
- [ ] P2 · Add `@Async` to BuildOrchestrator.triggerBuild() so HTTP response returns immediately
         — configure `AsyncConfig.java` with bounded thread pool
- [ ] P2 · Add Spring Boot Actuator for production monitoring:
         — /actuator/health, /actuator/metrics, /actuator/info
- [ ] TEST · GitHubActionsClientTest — mock HTTP, verify dispatch payload shape
- [ ] TEST · Webhook endpoint test — valid HMAC accepted, invalid rejected with 401
- [ ] TEST · Rate limiting test — 11th request in same hour returns 429
- [ ] TEST · End-to-end integration: create → generate → trigger → webhook callback → status=DONE
- [ ] DOCS · Update README with setup instructions for GitHub token + webhook secret
- [ ] DOCS · Write DEPLOYMENT.md for production deployment checklist
```

**Verification command:** `mvn clean test -pl conversion-service`

**Non-breaking guarantee:** All Week 1-3 endpoints continue working. GitHub Actions dispatch
is behind a client that degrades gracefully (logs warning if GITHUB_TOKEN not set). Subscription
check defaults to PRO — no user is blocked. Rate limiting is additive protection.

---

## 9. QUALITY GATES (agent MUST self-check before delivery)

### Java / Spring Boot Checklist
- [ ] `mvn clean test -pl conversion-service` passes with 0 failures
- [ ] No `RuntimeException` thrown directly — use typed exceptions
- [ ] All endpoints return structured `ErrorResponse` on failure (never raw stack traces)
- [ ] All `@RequestBody` DTOs have `@Valid` annotation on controller parameter
- [ ] No hardcoded secrets in `application.yml` — all via `${ENV_VAR:default}`
- [ ] MongoDB collection names are explicit (`@Document(collection = "...")`)
- [ ] `@CreatedDate` / `@LastModifiedDate` auditing works (verify with test)
- [ ] New entity fields are nullable with `@Builder.Default` — existing documents stay valid
- [ ] No N+1 query patterns — use repository methods with proper projections
- [ ] Controller methods return `ResponseEntity` with explicit status codes

### Electron Template Checklist
- [ ] `nodeIntegration: false` in all generated BrowserWindow configs
- [ ] `contextIsolation: true` in all generated BrowserWindow configs
- [ ] `preload` path uses `path.join(__dirname, ...)` — never concatenation
- [ ] No `eval()` in any generated main process code
- [ ] All IPC channels explicitly registered in generated `preload.js`
- [ ] `webSecurity: true` — never disabled
- [ ] Module `setup()` functions guard with `if (!config.modules?.includes(...)) return`
- [ ] Generated `package.json` excludes dev files from build: `"files"` array set

### Build Pipeline Checklist (Week 3-4)
- [ ] Build records are idempotent — duplicate trigger for same project+platform returns existing
- [ ] Webhook validates HMAC signature before processing
- [ ] Failed builds set status to FAILED with error message — never silently drop
- [ ] Build timeout: if no webhook callback in 30 min, mark as FAILED via scheduled task

### Backward Compatibility Checklist
- [ ] Existing MongoDB documents deserialise without errors after entity changes
- [ ] All 6 original endpoints produce same response shape (new fields may be null)
- [ ] `POST /{id}/generate` with no modules produces identical output to pre-overhaul
- [ ] Docker Compose boots without errors: `docker compose up --build`
- [ ] API gateway routing to conversion-service unaffected

---

## 10. ENVIRONMENT VARIABLES REFERENCE

All config via environment variables with safe defaults for local dev.

```bash
# conversion-service (Spring Boot)
MONGODB_URI=mongodb+srv://user:pass@cluster/webtodesk_conversions   # required in prod
SERVER_PORT=8082                                                     # default: 8082
EUREKA_URL=http://discovery-service:8761/eureka/                     # default: localhost

# Week 3+ additions
STORAGE_TYPE=local                           # "local" (dev) or "s3" (prod)
S3_BUCKET=webtodesk-builds                   # required if STORAGE_TYPE=s3
S3_REGION=us-east-1
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
CDN_URL=https://cdn.webtodesk.com

# Week 4 additions
GITHUB_TOKEN=ghp_...                         # PAT with workflow dispatch scope
GITHUB_REPO=owner/webtodesk                  # repo for build workflows
WEBHOOK_SECRET=...                           # HMAC shared secret for build callbacks
BUILD_RATE_LIMIT=10                          # max builds per user per hour (default: 10)
```

---

## 11. DOCKER COMPOSE CONTEXT

The conversion-service runs in `docker-compose.yml` alongside sibling services.
Current config (do not modify without explicit request):

```yaml
conversion-service:
  build:
    context: .
    dockerfile: conversion-service/Dockerfile
  container_name: conversion-service
  ports:
    - "8082:8082"
  environment:
    - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-service:8761/eureka/
  depends_on:
    - discovery-service
  networks:
    - webtodesk-net
```

**Week 3 target** — add MongoDB URI + storage config:
```yaml
conversion-service:
  # ... existing config ...
  environment:
    - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-service:8761/eureka/
    - MONGODB_URI=${MONGODB_URI:-mongodb://mongo:27017/webtodesk_conversions}
    - STORAGE_TYPE=local
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8082/conversions/health"]
    interval: 30s
    retries: 3
```

---

## 12. COMMON PITFALLS — NEVER DO THESE

| Anti-pattern | Correct approach |
|-------------|-----------------|
| Throwing raw `RuntimeException` | Use typed exceptions: `ProjectNotFoundException`, `BuildLimitExceededException` |
| Hardcoding MongoDB URI in `application.yml` | Use `${MONGODB_URI:mongodb://localhost:27017/webtodesk}` |
| Adding required fields to `ConversionProject` | New fields MUST be nullable with `@Builder.Default` — old documents must deserialise |
| Deleting or renaming existing endpoints | Only add new endpoints. Deprecate old ones with `@Deprecated` if needed. |
| Generating `nodeIntegration: true` in templates | Always `false` — use `preload.js` + `contextBridge` |
| Hardcoding template strings in Java service | Use Mustache templates in `resources/templates/electron/` |
| Synchronous HTTP call to GitHub Actions in request thread | Use `@Async` or message queue — return 202 Accepted immediately |
| Returning stack traces in error responses | `GlobalExceptionHandler` catches all — returns `ErrorResponse` only |
| Running all 4 weeks at once | Each week is atomic. Complete and test Week N before starting Week N+1. |
| Modifying `api-gateway` or `user-service` | Out of scope for this skill. Request changes explicitly if needed. |
| Skipping backward-compatibility test | Every Week 2+ change must verify old `/generate` output is identical |
| Fire-and-forget build dispatch | Always create `BuildRecord` first, then dispatch. Webhook updates record. |
| Polling for build status from frontend | Use WebSocket/SSE (future) or periodic GET with `Retry-After` header |
| `npm install` instead of `npm ci` in CI/CD | `npm ci` uses lockfile exactly — deterministic builds |
| Cross-compiling macOS `.dmg` on Linux runner | macOS builds MUST run on `macos-latest` — Apple restriction |
| Using `@PathVariable` without explicit name | Always use `@PathVariable("id")` — Java compiler doesn't preserve param names without `-parameters` flag |
| Using `HttpServletRequest` in `@ExceptionHandler` | Use `WebRequest` — works in both full Spring context and standalone MockMvc |
| Relying on `@WebMvcTest` or standalone MockMvc for complex tests | Use direct method invocation with `@InjectMocks` + Mockito for unit tests; live test HTTP layer separately |
| Using `de.flapdoodle.embed.mongo` for embedded MongoDB | Not compatible with Spring Boot 3.3.6; use Testcontainers (requires Docker) or mock repository |
| Starting conversion-service without `clean compile` first | Stale `.class` files cause `NoClassDefFoundError`; always `mvn clean compile spring-boot:run` |
| Frontend sends to `/conversions/...` directly | Frontend sends to `/conversion/conversions/...` — gateway strips `/conversion` prefix via `StripPrefix=1` |
| Assuming `common` module is needed by conversion-service | conversion-service does NOT depend on `common` — it has no JWT logic; auth is handled by gateway |

---

## 13. WEEK 1 LEARNINGS & PATTERNS

> These are hard-won lessons from the Week 1 implementation. Future weeks MUST respect these patterns.

### 13.1 Critical Bugs Discovered

**`@PathVariable` without explicit name causes 500 at runtime:**
- Java compilers do not preserve method parameter names in bytecode by default
- `@PathVariable String id` → `IllegalArgumentException: Name for argument of type [java.lang.String] not specified`
- **Fix:** Always use `@PathVariable("id") String id` with explicit name
- This affects ALL annotations that rely on parameter names: `@RequestParam`, `@PathVariable`, `@RequestHeader`
- The parent POM's `maven-compiler-plugin` does NOT include `-parameters` flag — do not add it, use explicit names instead

### 13.2 Testing Strategy That Works

**Controller tests — direct invocation, NOT MockMvc:**
- MockMvc standalone mode had persistent issues with path variable resolution and exception handler registration
- `@WebMvcTest` requires full Spring context loading which conflicts with security config
- **Winner:** `@ExtendWith(MockitoExtension.class)` + `@InjectMocks` + `@Mock` — tests controller logic directly
- HTTP layer is covered by live endpoint testing after `start-all.ps1`

**Service tests — standard Mockito:**
- `@ExtendWith(MockitoExtension.class)` + `@Mock ConversionRepository` + `@InjectMocks ConversionService`
- Use `assertThatThrownBy()` from AssertJ for exception testing
- Mock `save()` to return the entity with an `answer()` that captures the argument for assertion

**Test counts:** 28 total = 18 service + 10 controller

### 13.3 GlobalExceptionHandler Patterns

```java
// Use WebRequest (not HttpServletRequest) — works in all contexts
private String getPath(WebRequest request) {
    if (request instanceof ServletWebRequest swr) {
        return swr.getRequest().getRequestURI();
    }
    return request.getDescription(false);
}
```

**Handler order matters:**
1. `ProjectNotFoundException` → 404 (most specific)
2. `MethodArgumentNotValidException` → 400 (validation)
3. `MissingRequestHeaderException` → 400 (missing X-User-Email)
4. `IllegalArgumentException` → 400 (catch-all for bad args)
5. `Exception` → 500 (generic fallback — logs full stack trace server-side)

### 13.4 Frontend Error Contract

The frontend reads errors as: `err.response?.data?.message`

Our `ErrorResponse` record:
```java
public record ErrorResponse(String message, Map<String, String> errors, Instant timestamp, String path)
```

This means:
- `message` field is displayed to the user — keep it human-readable
- `errors` map is available for per-field validation display (frontend doesn't use it yet, but it's ready)
- The frontend `DashboardPage.tsx` line 49: `setFormError(err.response?.data?.message || 'Failed to create project')`

### 13.5 Environment & Build Gotchas

- **JAVA_HOME:** Must be set to `C:\Program Files\Java\jdk-17` on this Windows machine — system default points to JDK 11
- **Maven wrapper:** Root `mvnw.cmd` is shared. Services run via `cd service-dir && ..\mvnw.cmd`. conversion-service also has its own local copy.
- **Stale classes:** After editing Java files, always run `mvn clean compile` before `spring-boot:run`. The `run` goal doesn't always recompile changed inner classes (e.g., `ConversionProject$ConversionStatus`).
- **Port conflicts:** If a previous Java process is still running on 8082, the service won't start. Kill with `Get-Process -Name java | Stop-Process -Force`.

### 13.6 API Gateway Route Mapping

```
Frontend → /conversion/conversions/{id}
                │
          Gateway strips /conversion prefix (StripPrefix=1)
                │
          conversion-service receives → /conversions/{id}
```

The conversion-service controller maps to `/conversions` (no `/conversion` prefix). The gateway's route config handles the prefix stripping. **Never add `/conversion` to controller `@RequestMapping`.**

### 13.7 Validation Rules Reference (post-Week 1)

**CreateConversionRequest:**
- `projectName`: `@NotBlank`, `@Size(max=64)`, `@Pattern("[a-zA-Z0-9 _-]+")`
- `websiteUrl`: `@NotBlank`, `@Pattern("^https://.*")` with message "Website URL must start with https://"
- `appTitle`: `@NotBlank`, `@Size(max=128)`
- `iconFile`: optional (defaults to `"icon.ico"` in service layer)

**UpdateConversionRequest:**
- `websiteUrl`: `@Pattern("^https://.*")` (nullable — validated only if present)
- `appTitle`: `@Size(max=128)` (nullable)
- `currentVersion`: `@Pattern("^\\d+\\.\\d+\\.\\d+$")` — semver X.Y.Z (nullable)
- `projectName`, `iconFile`: no validation constraints (nullable)

---

*WebToDesk Conversion Service Overhaul Skill — v2.1 — 2026-03-27*
*Week 1 COMPLETE. Aligned to actual codebase with full project context.*
*Stack: Spring Boot 3.3.6 + MongoDB + Eureka + React 19/Vite 6.*
*Ref: docs/conversion_service-refrencer.md for target vision.*
