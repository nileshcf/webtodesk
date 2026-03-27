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
- **Server-Side .exe Building** — Full local execution of `npm install` and `electron-builder` to generate `.exe` files and stream real-time logs via SSE
- **Cloud Object Storage** — Automatically uploads generated artifacts to Cloudflare R2 for secure, scalable distribution
- **Screenshot & Recording Protection** — OS-level content protection with visual deterrent overlay
- **DevTools Blocking** — Prevents end-users from inspecting or modifying the wrapped app
- **Cross-Platform Builds** — Generated projects support Windows (.exe/NSIS), macOS (.dmg), and Linux (AppImage)
- **User Authentication** — JWT-based auth with access/refresh token rotation and Redis-backed blacklisting
- **User Profile Management** — Update username, display name, phone, and avatar
- **Project Dashboard** — Create, list, update, delete, and generate conversion projects
- **Version Management** — Track and update version numbers per project
- **Custom Branding** — Set app title, project name, and custom icon per conversion
- **Service Discovery** — Eureka-based service registry for automatic microservice routing
- **API Gateway** — Centralized routing, JWT validation, CORS, and header forwarding

### Planned

- ⚠️ **Subscription & Billing** — Stripe/Razorpay integration with plan tiers and usage limits
- ⚠️ **Email Verification** — Field exists but flow is not implemented
- ⚠️ **Password Reset** — Endpoint references exist but no implementation
- ⚠️ **Admin Panel** — Role enum exists (ROLE_ADMIN) but no admin UI or endpoints
- ⚠️ **File Upload** — Icon file upload (currently accepts filename string only)
- ⚠️ **CI/CD Pipeline** — No GitHub Actions or GitLab CI configuration
- ⚠️ **Monitoring & Observability** — Health checks, metrics, structured logging

---

## Tech Stack

| Layer          | Technology                                                        |
| -------------- | ----------------------------------------------------------------- |
| **Frontend**   | React 19, TypeScript, Vite 6, TailwindCSS 3, Framer Motion, Axios |
| **API Gateway**| Spring Cloud Gateway, Spring Security (WebFlux), Eureka Client    |
| **User Service** | Spring Boot 3.3.6, Spring Data JPA, Spring Security, Redis      |
| **Conversion Service** | Spring Boot 3.3.6, Spring Data MongoDB, Validation        |
| **Discovery**  | Spring Cloud Netflix Eureka Server                                |
| **Auth**       | JWT (jjwt 0.12.3), BCrypt, Redis token blacklisting              |
| **Databases**  | PostgreSQL (users), MongoDB (conversions), Redis (token blacklist) |
| **Build**      | Maven multi-module, Vite                                          |
| **Containers** | Docker (multi-stage builds), Docker Compose                       |
| **Language**   | Java 17, TypeScript 5.7                                           |

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
| `USER_SERVICE_DB_PASSWORD` | user-service (prod) | Prod only | Production DB password | `****` |

> ⚠️ **No `.env.example` file currently exists in the repository.** This is a gap that should be addressed.

---

## Running with Docker

```bash
# Build and start all services
docker-compose up --build

# Services will be available at:
#   Discovery:  http://localhost:8761
#   Gateway:    http://localhost:8080
#   User:       http://localhost:8081
#   Converter:  http://localhost:8082
```

> ⚠️ **Note**: The current `docker-compose.yml` does not include PostgreSQL, MongoDB, or Redis containers. You must provide external database instances or add database services to the compose file.

> ⚠️ **Note**: Dockerfiles are missing for `discovery-service` and `conversion-service`. Only `api-gateway` and `user-service` have Dockerfiles.

---

## Running Tests

> ⚠️ **No tests currently exist in the codebase.** This is a critical gap. The project has no unit tests, integration tests, or end-to-end tests.

When tests are added:

```bash
# Backend (Maven)
./mvnw test                           # Run all module tests
./mvnw test -pl user-service          # Run user-service tests only

# Frontend
cd frontend
npm test                              # (test script not yet configured)
```

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

> ⚠️ **Not yet implemented.** See [`docs/SUBSCRIPTION_AND_BILLING.md`](./docs/SUBSCRIPTION_AND_BILLING.md) for the planned billing architecture and [`docs/IMPROVEMENTS.md`](./docs/IMPROVEMENTS.md) for the implementation roadmap.

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

- [ARCHITECTURE.md](./docs/ARCHITECTURE.md) — System architecture with Mermaid diagrams
- [FILE_STRUCTURE.md](./docs/FILE_STRUCTURE.md) — Annotated file tree
- [DB_SCHEMA.md](./docs/DB_SCHEMA.md) — Database schema documentation
- [API_REFERENCE.md](./docs/API_REFERENCE.md) — Complete API endpoint reference
- [SUBSCRIPTION_AND_BILLING.md](./docs/SUBSCRIPTION_AND_BILLING.md) — Billing model documentation
- [DEPLOYMENT.md](./docs/DEPLOYMENT.md) — Production deployment guide
- [IMPROVEMENTS.md](./docs/IMPROVEMENTS.md) — Prioritized improvement recommendations
