---
title: WebToDesk
emoji: 🖥️
colorFrom: blue
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
short_description: Convert any website into a premium desktop application
---

# WebToDesk

**Convert any website into a premium desktop application — in seconds.**

WebToDesk is a SaaS platform that accepts any website URL and user configuration via a modern React frontend, passes those settings to a backend microservices architecture, and generates a fully packaged Electron desktop application (.exe / cross-platform) with enterprise features like screenshot protection, DevTools blocking, and custom branding baked in.

---

## Features

### Current

- **Website-to-Desktop Conversion** — Paste any URL and generate a ready-to-build Electron project
- **Server-Side .exe / .deb Building** — Full local execution of `npm install` and `electron-builder` inside Docker to generate `.exe` (Windows via Wine) or `.deb` (Linux) files and stream real-time logs via SSE
- **Cloud Object Storage** — Automatically uploads generated artifacts to Cloudflare R2 for secure, scalable distribution
- **Licensing System** — Four-tier licensing model (Trial/Starter/Pro/Lifetime) with build limits and expiry management
- **OS-Specific Builds** — Cross-platform builds for Windows (.exe/.msi), Linux (.AppImage/.deb/.rpm), and macOS (.dmg/.zip)
- **Priority Queue Routing** — Tier-based build queue management with normal and priority executors
- **Screenshot & Recording Protection** — OS-level content protection with visual deterrent overlay
- **DevTools Blocking** — Prevents end-users from inspecting or modifying the wrapped app
- **Version Upgrade System** — Automatic version detection and upgrade with license persistence
- **License Expiry Enforcement** — Runtime license validation with blocking expiry screen
- **Feature Module System** — Modular architecture with tier-based feature availability
- **User Authentication** — JWT-based auth with access/refresh token rotation and Redis-backed blacklisting
- **User Profile Management** — Update username, display name, phone, and avatar
- **Project Dashboard** — Multi-step wizard with tier selection, OS configuration, and feature toggles
- **Real-time Build Monitoring** — SSE-based build progress tracking with queue status
- **Custom Branding** — Set app title, project name, and custom icon per conversion
- **Service Discovery** — Eureka-based service registry for automatic microservice routing
- **API Gateway** — Centralized routing, JWT validation, CORS, and header forwarding
- **Frontend Integration** — Complete React frontend with TypeScript types and custom hooks

### Planned

- ⚠️ **Subscription & Billing** — Stripe/Razorpay integration with plan tiers and usage limits (backend implemented, frontend integration in progress)
- ⚠️ **Email Verification** — Field exists but flow is not implemented
- ⚠️ **Password Reset** — Endpoint references exist but no implementation
- ⚠️ **Admin Panel** — Role enum exists (ROLE_ADMIN) but no admin UI or endpoints
- ⚠️ **File Upload** — Icon file upload (currently accepts filename string only)
- ⚠️ **CI/CD Pipeline** — GitHub Actions workflow pending (Week 4)
- ⚠️ **VersionUpgradeService** — Automatic version upgrade with license persistence (Week 4)
- ⚠️ **OpenAPI Docs** — Springdoc OpenAPI integration pending (Week 4)
- ⚠️ **Monitoring & Observability** — Prometheus metrics, structured logging

---

## Tech Stack

| Layer          | Technology                                                        |
| -------------- | ----------------------------------------------------------------- |
| **Frontend**   | React 19, TypeScript, Vite 6, TailwindCSS 3, Framer Motion, Axios |
| **API Gateway**| Spring Cloud Gateway, Spring Security (WebFlux), Eureka Client    |
| **User Service** | Spring Boot 3.3.6, Spring Data JPA, Spring Security, Redis      |
| **Conversion Service** | Spring Boot 3.3.6, Spring Data MongoDB, Validation, Licensing |
| **Discovery**  | Spring Cloud Netflix Eureka Server                                |
| **Auth**       | JWT (jjwt 0.12.3), BCrypt, Redis token blacklisting              |
| **Databases**  | PostgreSQL (users), MongoDB (conversions), Redis (token blacklist) |
| **Build**      | Maven multi-module, Vite                                          |
| **Containers** | Docker (multi-stage builds), Docker Compose                       |
| **Language**   | Java 17, TypeScript 5.7                                           |
| **Licensing**  | Tier-based system (Trial/Starter/Pro/Lifetime) with build limits |
| **Build Queue** | Priority queue system with OS-specific routing                  |

---

## Prerequisites

- **Java 17+** (JDK)
- **Maven 3.9+** (or use included `mvnw` wrapper)
- **Node.js 18+** and **npm 9+**
- **PostgreSQL 15+** (or a Neon/Supabase cloud instance)
- **MongoDB 6+** (or a MongoDB Atlas cloud instance)
- **Redis 7+** (or an Upstash cloud instance)
- **Docker & Docker Compose** (optional, for containerized setup)

---

## AI Environment Contract (No Guesswork)

Use this contract for all local runs so Java/tooling setup is deterministic and never guessed/reset.

### Required Runtime Versions

- `Java`: **17** (JDK, not JRE)
- `Node.js`: **18+**
- `npm`: **9+**
- `Maven`: use project wrapper (`mvnw`), no global Maven required

### Local Machine Baseline (This Workspace)

- Preferred local `JAVA_HOME`: `C:\Program Files\Java\jdk-17`
- Agents should assume this path first on this machine before trying alternatives.

### Where Java Is Set and Used

- Script startup path: `start-all.ps1`
  - Uses current `JAVA_HOME` by default.
  - Can be forced per run via `-JavaHome "C:\\Program Files\\Java\\jdk-17"`.
- Maven build/run path:
  - Root wrapper: `./mvnw`
  - Module wrappers: `api-gateway/mvnw`, `user-service/mvnw`, `conversion-service/mvnw`

### One-Time Verification (Windows PowerShell)

```powershell
java -version
$env:JAVA_HOME
.\mvnw -v
node -v
npm -v
```

Expected: Java reports version 17.x and Maven runs via wrapper.

### Deterministic Install + Start (AI/Human)

```powershell
# Frontend dependencies
npm --prefix .\frontend ci

# Backend compile check (all modules)
.\mvnw -q -DskipTests compile

# Start all services without prompts and with parseable output
.\start-all.ps1 -NonInteractive -NoBrowserPrompt -OutputJson
```

### Rules for Agents

- Do not overwrite global Java settings unless explicitly requested.
- Prefer passing `-JavaHome` to `start-all.ps1` instead of mutating machine state.
- Use `mvnw`/`mvnw.cmd` and avoid assuming globally installed Maven.
- Use `npm --prefix .\frontend ci` for reproducible frontend dependency installs.

---

## Local Development Setup

### 1. Clone the repository

```bash
git clone https://github.com/your-org/webtodesk.git
cd webtodesk
```

### 2. Configure environment

Create/edit the following configuration files with your database credentials:

- `common/src/main/resources/application.yml` — JWT secrets
- `user-service/src/main/resources/application-dev.yml` — PostgreSQL + Redis URLs
- `conversion-service/src/main/resources/application.yml` — MongoDB URI

> ⚠️ **IMPORTANT**: The current codebase has credentials hardcoded in YAML files. Before any production deployment, move ALL secrets to environment variables. See the Environment Variables table below.

### 3. Start backend services (in order)

```powershell
# Option A: Use the PowerShell script (Windows)
.\start-all.ps1

# Option B: Start manually (any OS) — each in a separate terminal
cd discovery-service && ./mvnw spring-boot:run    # Port 8761 — start first, wait 15s
cd user-service && ./mvnw spring-boot:run          # Port 8081
cd conversion-service && ./mvnw spring-boot:run    # Port 8082
cd api-gateway && ./mvnw spring-boot:run           # Port 8080
```

### 4. Start frontend

```bash
cd frontend
npm install
npm run dev    # http://localhost:5173
```

### 5. Verify

- **Eureka Dashboard**: http://localhost:8761
- **API Gateway**: http://localhost:8080
- **Frontend**: http://localhost:5173

### 6. Automation Scripts (Windows)

These scripts support both interactive use (for humans) and deterministic non-interactive use (for AI/automation).

- `start-all.ps1` — starts all local services with readiness checks
- `docker-rebuild.ps1` — rebuilds Docker image with cache/no-cache and cleanup options
- `docker-start.ps1` — starts container on selected ports with optional cleanup of conflicts
- `registry-push.ps1` — build/tag/push image to GHCR or Docker Hub for team testing
- `registry-pull-run.ps1` — pull shared registry image and run container directly
- `git-operations.ps1` — interactive git UI + command-mode actions for automation
- `ai-doc-sync.ps1` — one-go app-level update brief for `README.md`, `CHANGELOG.md`, `docs/**`, and `skills/**`

#### Quick Human Usage

```powershell
.\start-all.ps1
.\docker-rebuild.ps1 -RemoveOldImages -PruneDangling
.\docker-start.ps1 -StopExisting -HostPort 7860
.\registry-push.ps1 -GitHubRepo <github-user-or-org>/webtodesk -BuildFirst -Tag latest -ExtraTags v1.8.0 -RunLogin
.\registry-pull-run.ps1 -GitHubRepo <github-user-or-org>/webtodesk -Tag latest -StopExisting
.\git-operations.ps1 -Action interactive
.\ai-doc-sync.ps1 -SinceRef HEAD~3
```

#### AI / Non-Interactive Usage

```powershell
.\start-all.ps1 -NonInteractive -NoBrowserPrompt -OutputJson
.\docker-rebuild.ps1 -NoCache -RemoveOldImages -PruneDangling -NonInteractive -OutputJson
.\docker-start.ps1 -StopExisting -KillPortProcess -NonInteractive -OutputJson
.\registry-push.ps1 -GitHubRepo <github-user-or-org>/webtodesk -BuildFirst -Tag latest -ExtraTags v1.8.0 -RunLogin -NonInteractive -OutputJson
.\registry-pull-run.ps1 -GitHubRepo <github-user-or-org>/webtodesk -Tag latest -StopExisting -PullAlways -NonInteractive -OutputJson
.\git-operations.ps1 -Action status -OutputJson
.\git-operations.ps1 -Action switch -Branch develop -NonInteractive -OutputJson
.\git-operations.ps1 -Action merge -TargetBranch feature/my-change -NonInteractive -OutputJson
.\ai-doc-sync.ps1 -SinceRef HEAD~5 -OutputJson
.\ai-doc-sync.ps1 -OnlyWorkingTree -RunAgent -AgentCommand "claude -p {PROMPT_FILE}" -NonInteractive
```

> Tip: prefer `-OutputJson` for machine-readable logs/results in agent workflows.

---

## Environment Variables Reference

| Variable | Service | Required | Description | Example |
|---|---|---|---|---|
| `JWT_ACCESS_SECRET` | common, api-gateway, user-service | Yes | HMAC key for access tokens (min 32 chars) | `7c41b134c9f40a58708c0d7b67ccea40` |
| `JWT_REFRESH_SECRET` | common, api-gateway, user-service | Yes | HMAC key for refresh tokens (min 32 chars) | `3b842a72031ec37f34b3a563014bde8c` |
| `SPRING_DATASOURCE_URL` | user-service | Yes | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/webtodesk` |
| `SPRING_DATASOURCE_USERNAME` | user-service | Yes | PostgreSQL username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | user-service | Yes | PostgreSQL password | `secret` |
| `SPRING_DATA_REDIS_URL` | user-service | Yes | Redis connection URL | `redis://localhost:6379` |
| `SPRING_DATA_MONGODB_URI` | conversion-service | Yes | MongoDB connection URI | `mongodb://localhost:27017/webtodesk` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | all services | Yes | Eureka server URL | `http://localhost:8761/eureka/` |
| `SPRING_PROFILES_ACTIVE` | all services | No | Active Spring profile | `dev` / `prod` |
| `R2_ACCOUNT_ID` | conversion-service | Yes (prod) | Cloudflare R2 account ID | `b34ccabf...` |
| `R2_ACCESS_KEY_ID` | conversion-service | Yes (prod) | R2 access key | `...` |
| `R2_SECRET_ACCESS_KEY` | conversion-service | Yes (prod) | R2 secret key | `...` |
| `R2_BUCKET` | conversion-service | No | R2 bucket name | `webtodesk-builds` |
| `R2_PUBLIC_URL` | conversion-service | No | R2 public CDN URL | `https://pub-xxx.r2.dev` |
| `DEVELOPMENT_BUILD` | conversion-service | No | Bypass license validation + tier gating | `true` |
| `WEBTODESK_BUILD_TARGET_PLATFORM` | conversion-service | No | Override build target: `auto`/`win`/`linux`/`mac` | `auto` |
| `BUILD_OUTPUT_DIR` | conversion-service | No | Workspace dir for electron builds | `/tmp/webtodesk-builds` |

> Copy `.env` to configure all secrets. `DEVELOPMENT_BUILD=true` is set in `.env` for the Docker container to bypass license checks during development.

---

## Running with Docker

```bash
# Build and start the monolith container (all services in one image)
docker compose build --build-arg NODE_MAJOR=20 --build-arg ELECTRON_VERSION=38.2.2 --build-arg ELECTRON_BUILDER_VERSION=26.0.12
docker compose up -d

# Or use the helper scripts (Windows)
.\docker-rebuild.ps1
.\docker-start.ps1 -StopExisting

# App is available at:
#   Frontend + API:  http://localhost:7860
```

> **Monolith mode**: All four Spring Boot services + React frontend run in a single container behind Nginx on port 7860. Cloud services (Neon PostgreSQL, Upstash Redis, MongoDB Atlas, Cloudflare R2) are injected via `.env` — no local database containers needed.

> **tmpfs build workspace**: The build workspace (`/tmp/webtodesk-builds`) is mounted as `tmpfs` with `exec` for maximum I/O speed during `npm install` and `electron-builder` execution. Docker's default `noexec` tmpfs flag is explicitly overridden — this is required for electron-builder to run.

> **Windows builds via Wine**: The container includes Wine 6 (wine32 + wine64 + i386 arch) for cross-compiling Windows `.exe` installers on Linux.

### Share One Image for Team Testing

```powershell
# Publisher machine: build + push to registry (GHCR shown)
.\registry-push.ps1 -GitHubRepo <github-user-or-org>/webtodesk -BuildFirst -Tag latest -ExtraTags v1.8.0 -RunLogin

# Tester machine: pull + run the exact same image
.\registry-pull-run.ps1 -GitHubRepo <github-user-or-org>/webtodesk -Tag latest -StopExisting -PullAlways
```

> Use immutable version tags (for example, `v1.8.0`) for reproducible team testing, and keep `latest` as a convenience tag.

---

## Running Tests

**83 tests passing** across `conversion-service`.

```powershell
# Windows (PowerShell) — conversion-service full suite
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd clean test -pl conversion-service "-DMONGODB_URI=mongodb://localhost:27017/test"

# All modules
.\mvnw.cmd test
```

| Test Class | Count | Scope |
|---|---|---|
| `ConversionControllerTest` | 10 | Controller layer |
| `LicenseControllerTest` | 12 | License endpoints |
| `ConversionServiceTest` | 18 | Service + entity |
| `LicenseServiceTest` | 17 | Tier validation, quota |
| `ModuleRegistryTest` | 19 | Module tier gating |
| `TemplateEngineTest` | 7 | Mustache rendering |
| **Total** | **83** | **All passing** |

---

## API Overview

All API calls go through the **API Gateway** at `http://localhost:8080`.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/user/auth/register` | No | Register a new user |
| `POST` | `/user/auth/login` | No | Login and receive JWT tokens |
| `POST` | `/user/auth/refresh` | No | Refresh access token |
| `POST` | `/user/auth/logout` | Yes | Logout and blacklist refresh token |
| `GET` | `/user/me` | Yes | Get current user profile |
| `PUT` | `/user/me` | Yes | Update current user profile |
| `POST` | `/conversion/conversions` | Yes | Create a new conversion project |
| `GET` | `/conversion/conversions` | Yes | List user's conversion projects |
| `GET` | `/conversion/conversions/{id}` | Yes | Get a specific project |
| `PUT` | `/conversion/conversions/{id}` | Yes | Update a project |
| `DELETE` | `/conversion/conversions/{id}` | Yes | Delete a project |
| `POST` | `/conversion/conversions/{id}/generate` | Yes | Generate Electron project files |

See [`docs/API_REFERENCE.md`](./docs/API_REFERENCE.md) for complete endpoint documentation.

---

## Deployment Guide (Production)

See [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md) for full production deployment instructions covering:

- Docker / Kubernetes deployment
- Environment variable injection
- SSL / domain configuration
- Database migration
- Monitoring & alerting
- Rollback procedures

---

## Subscription & Billing Model

**✅ Implemented (Backend + Frontend Integration)**

WebToDesk now features a comprehensive four-tier licensing system:

| Tier | Price | Duration | Build Limits | Features |
|------|--------|-----------|--------------|----------|
| **Trial** | Free | 30 days | 4 builds total (2 apps × 1 update) | Basic features, normal queue |
| **Starter** | $9/mo | 1 year | 120 builds (10/month) | Priority queue, advanced features |
| **Pro** | $29/mo | 5 years | 3,000 builds (50/month) | All features, priority support |
| **Lifetime** | $299 | Unlimited | Unlimited (fair use) | All features, lifetime updates |

**Key Features:**
- **OS-specific builds** for Windows, Linux, and macOS
- **Priority queue routing** for paid tiers
- **License expiry enforcement** with blocking screen
- **Automatic version upgrades** with license persistence
- **Real-time build monitoring** with SSE updates
- **Feature module system** with tier-based availability

See [`skills/conversion-service.md`](./skills/conversion-service.md) for complete technical implementation details and [`skills/FEATURES.md`](./skills/FEATURES.md) for feature specifications.

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes with appropriate tests
4. Ensure all tests pass: `./mvnw test && cd frontend && npm test`
5. Commit with a descriptive message: `git commit -m "feat: add payment webhook handler"`
6. Push to your branch: `git push origin feature/my-feature`
7. Open a Pull Request with a clear description of changes

### Conventions

- **Backend**: Follow standard Spring Boot conventions; use Lombok; use Java records for DTOs
- **Frontend**: Functional components with hooks; TailwindCSS for styling; TypeScript strict mode
- **Git**: Conventional commits (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`)
- **Branches**: `development` (production), `develop` (integration), `feature/*`, `fix/*`

---

## License

This project is proprietary software. All rights reserved.

---

## Related Documentation

### Core Documentation
- [skills/FEATURES.md](./skills/FEATURES.md) — **Complete feature specifications and licensing system**
- [skills/conversion-service.md](./skills/conversion-service.md) — **Technical implementation details and architecture**

### Legacy Documentation
- [ARCHITECTURE.md](./docs/ARCHITECTURE.md) — System architecture with Mermaid diagrams
- [FILE_STRUCTURE.md](./docs/FILE_STRUCTURE.md) — Annotated file tree
- [DB_SCHEMA.md](./docs/DB_SCHEMA.md) — Database schema documentation
- [API_REFERENCE.md](./docs/API_REFERENCE.md) — Complete API endpoint reference
- [SUBSCRIPTION_AND_BILLING.md](./docs/SUBSCRIPTION_AND_BILLING.md) — Billing model documentation
- [DEPLOYMENT.md](./docs/DEPLOYMENT.md) — Production deployment guide
- [IMPROVEMENTS.md](./docs/IMPROVEMENTS.md) — Prioritized improvement recommendations

### Frontend Integration
- **TypeScript Types**: `frontend/src/types/license.ts`, `frontend/src/types/build.ts`, `frontend/src/types/modules.ts`, `frontend/src/types/upgrade.ts`
- **API Services**: `frontend/src/services/licenseApi.ts`, `frontend/src/services/buildApi.ts`, `frontend/src/services/versionApi.ts`
- **Custom Hooks**: `frontend/src/hooks/useLicense.ts`, `frontend/src/hooks/useBuildQueue.ts`
- **Component Architecture**: Multi-step wizard, license management, build queue monitoring
