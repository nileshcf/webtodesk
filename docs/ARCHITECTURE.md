# WebToDesk — Architecture Documentation

This document describes the system architecture of WebToDesk, including service topology, data flows, database schema relationships, and frontend component structure.

---

## Table of Contents

1. [System Architecture Diagram](#1-system-architecture-diagram)
2. [Sequence Diagram — Website Conversion Flow](#2-sequence-diagram--website-conversion-flow)
3. [Sequence Diagram — Subscription & Payment Flow](#3-sequence-diagram--subscription--payment-flow)
4. [Entity Relationship Diagram (ERD)](#4-entity-relationship-diagram-erd)
5. [Component Diagram — Frontend](#5-component-diagram--frontend)

---

## 1. System Architecture Diagram

Shows all services, databases, queues, external APIs, and the client with data flow arrows.

```mermaid
graph TB
    subgraph Client["Client (Browser)"]
        FE["React Frontend<br/>:5173 (Vite Dev)"]
    end

    subgraph Gateway["API Gateway :8080"]
        GW["Spring Cloud Gateway<br/>(WebFlux + Security)"]
        JWT_FILTER["JWT Validation Filter"]
        HEADER_FWD["Header Forwarding Filter<br/>(X-User-Id, X-User-Email, X-User-Roles)"]
        ROUTER["Route Config<br/>/user/** → user-service<br/>/conversion/** → conversion-service"]
    end

    subgraph Discovery["Discovery Service :8761"]
        EUREKA["Netflix Eureka Server"]
    end

    subgraph UserSvc["User Service :8081"]
        AUTH_CTRL["AuthController<br/>/auth/*"]
        USER_CTRL["UserController<br/>/me"]
        AUTH_SVC["AuthService"]
        USER_SVC["UserService"]
    end

    subgraph ConvSvc["Conversion Service :8082"]
        CONV_CTRL["ConversionController<br/>/conversions/*"]
        CONV_SVC["ConversionService"]
        ELECTRON_GEN["Electron Project Generator"]
    end

    subgraph Databases["Data Stores"]
        PG[("PostgreSQL<br/>(Users, Profiles, Roles)")]
        MONGO[("MongoDB<br/>(Conversion Projects)")]
        REDIS[("Redis<br/>(Token Blacklist)")]
    end

    subgraph Common["Common Module (JAR)"]
        JWT_PROV["JwtTokenProvider"]
        JWT_VAL["JwtValidator"]
        JWT_CONST["JwtConstants"]
        ERR_DTO["ErrorResponse DTO"]
    end

    FE -->|"HTTP (via Vite proxy)"| GW
    GW --> JWT_FILTER --> HEADER_FWD --> ROUTER

    ROUTER -->|"/user/**<br/>(StripPrefix=1)"| AUTH_CTRL
    ROUTER -->|"/user/**<br/>(StripPrefix=1)"| USER_CTRL
    ROUTER -->|"/conversion/**<br/>(StripPrefix=1)"| CONV_CTRL

    AUTH_CTRL --> AUTH_SVC
    USER_CTRL --> USER_SVC
    CONV_CTRL --> CONV_SVC
    CONV_SVC --> ELECTRON_GEN

    AUTH_SVC --> PG
    AUTH_SVC --> REDIS
    USER_SVC --> PG
    CONV_SVC --> MONGO

    GW -.->|"registers"| EUREKA
    UserSvc -.->|"registers"| EUREKA
    ConvSvc -.->|"registers"| EUREKA

    GW -.->|"uses"| Common
    UserSvc -.->|"uses"| Common

    style FE fill:#1a1a2e,stroke:#2997ff,color:#fff
    style GW fill:#1a1a2e,stroke:#bf5af2,color:#fff
    style EUREKA fill:#1a1a2e,stroke:#30d158,color:#fff
    style PG fill:#0d1b2a,stroke:#ff9f0a,color:#fff
    style MONGO fill:#0d1b2a,stroke:#30d158,color:#fff
    style REDIS fill:#0d1b2a,stroke:#ff453a,color:#fff
```

### Service Ports

| Service | Port | Technology |
|---------|------|-----------|
| Frontend (Vite dev server) | 5173 | React + Vite |
| API Gateway | 8080 | Spring Cloud Gateway (WebFlux) |
| User Service | 8081 | Spring Boot (Servlet) |
| Conversion Service | 8082 | Spring Boot (Servlet) |
| Discovery Service | 8761 | Eureka Server |

### Key Architectural Decisions

- **Gateway-only auth**: The API Gateway performs all JWT validation. Downstream services (user-service, conversion-service) trust the gateway and permit all requests internally, relying on forwarded `X-User-*` headers for identity.
- **Shared common module**: JWT logic (provider, validator, constants) is in a `common` Maven module used by both the gateway and user-service.
- **Stateless sessions**: No server-side session storage. JWTs are validated per-request. Refresh token blacklisting uses Redis with TTL matching token expiry.
- **Service discovery**: Eureka enables load-balanced routing (`lb://service-name`) through the gateway without hardcoded service URLs.

---

## 2. Sequence Diagram — Website Conversion Flow

End-to-end flow from user submitting a URL to downloading the generated Electron project files.

```mermaid
sequenceDiagram
    actor User
    participant FE as React Frontend
    participant GW as API Gateway :8080
    participant CS as Conversion Service :8082
    participant DB as MongoDB

    Note over User,DB: Step 1 — Create Conversion Project

    User->>FE: Fill form (URL, appTitle, projectName, icon)
    FE->>GW: POST /conversion/conversions<br/>[Authorization: Bearer <token>]
    GW->>GW: Validate JWT (access token)
    GW->>GW: Extract claims → X-User-Email header
    GW->>CS: POST /conversions<br/>[X-User-Email: user@example.com]
    CS->>CS: Sanitize project name<br/>(lowercase, alphanumeric+hyphens)
    CS->>DB: Save ConversionProject<br/>(status: DRAFT)
    DB-->>CS: Saved document
    CS-->>GW: 200 OK — ConversionResponse
    GW-->>FE: 200 OK — ConversionResponse
    FE-->>User: Show project in dashboard

    Note over User,DB: Step 2 — Generate Electron Project

    User->>FE: Click "Generate" button
    FE->>GW: POST /conversion/conversions/{id}/generate<br/>[Authorization: Bearer <token>]
    GW->>GW: Validate JWT
    GW->>CS: POST /conversions/{id}/generate
    CS->>DB: Find project by ID
    DB-->>CS: ConversionProject
    CS->>CS: Generate config.js (project settings)
    CS->>CS: Generate main.js (Electron main process)
    CS->>CS: Generate preload.js (screenshot protection)
    CS->>CS: Generate package.json (build config)
    CS->>DB: Update status → READY
    CS-->>GW: 200 OK — ElectronConfigResponse<br/>{files: {filename → content}}
    GW-->>FE: 200 OK — ElectronConfigResponse
    FE-->>User: Show modal with generated files

    Note over User,DB: Step 3 — Download Files

    User->>FE: Click "Download All Files"
    FE->>FE: Create Blob per file<br/>Trigger browser download
    FE-->>User: Files downloaded locally

    Note over User: Step 4 — Build Locally (Manual)
    Note over User: User runs: npm install && npm run dist<br/>to produce .exe/.dmg/AppImage
```

### ⚠️ Missing: Server-Side Build Pipeline

The current implementation **does not build the .exe on the server**. It returns Electron source files as JSON, and the user must:

1. Download the files
2. Add an `icon.ico` to a `build/` folder
3. Run `npm install` and `npm run dist` locally with Electron Builder

A server-side build pipeline (job queue → cloud builder → upload to storage → notify user) is planned but not implemented.

---

## 3. Sequence Diagram — Subscription & Payment Flow

> ⚠️ **NOT YET IMPLEMENTED** — This diagram represents the **planned** architecture.

```mermaid
sequenceDiagram
    actor User
    participant FE as React Frontend
    participant GW as API Gateway
    participant US as User Service
    participant PS as Payment Service ⚠️ PLANNED
    participant PG as Payment Gateway<br/>(Stripe / Razorpay) ⚠️ PLANNED
    participant DB as PostgreSQL

    Note over User,DB: Plan Selection & Checkout

    User->>FE: Select plan (Free / Basic / Pro / Enterprise)
    FE->>GW: POST /payment/checkout<br/>{planId, userId}
    GW->>PS: Forward request
    PS->>PG: Create checkout session
    PG-->>PS: Session URL
    PS-->>FE: Redirect URL
    FE->>User: Redirect to payment page

    Note over User,DB: Payment Processing

    User->>PG: Enter payment details & confirm
    PG-->>User: Payment success page
    PG->>PS: Webhook: payment_intent.succeeded
    PS->>PS: Verify webhook signature
    PS->>DB: Create/update Subscription record<br/>(plan, status: ACTIVE, period dates)
    PS->>US: Update user plan tier
    PS-->>PG: 200 OK (acknowledge webhook)

    Note over User,DB: Plan Enforcement (on conversion)

    User->>FE: Create conversion project
    FE->>GW: POST /conversion/conversions
    GW->>CS: Forward with X-User-Email
    CS->>PS: Check user plan & usage limits ⚠️ PLANNED
    PS->>DB: Query subscription + conversion count
    alt Within limits
        PS-->>CS: Allowed
        CS->>CS: Proceed with conversion
    else Limit exceeded
        PS-->>CS: 403 Plan limit reached
        CS-->>FE: 403 Upgrade required
        FE-->>User: Show upgrade prompt
    end

    Note over User,DB: Failed Payment Handling ⚠️ PLANNED

    PG->>PS: Webhook: invoice.payment_failed
    PS->>DB: Update subscription status → PAST_DUE
    PS->>PS: Send email notification
    Note over PS: Grace period (7 days)
    PS->>DB: If still unpaid → status: CANCELLED
```

### Planned Subscription Tiers

| Plan | Conversions/Month | Server Builds | Custom Icons | Price |
|------|-------------------|---------------|-------------|-------|
| Free | 2 | No | No | $0 |
| Basic | 10 | Yes | Yes | $9/mo |
| Pro | 50 | Yes | Yes | $29/mo |
| Enterprise | Unlimited | Yes | Yes | Custom |

---

## 4. Entity Relationship Diagram (ERD)

```mermaid
erDiagram
    USERS {
        varchar id PK "UUID, auto-generated"
        varchar username UK "unique, not null"
        varchar email UK "unique, not null"
        varchar password "bcrypt hash, not null"
        boolean email_verified "default false"
        timestamp created_at "auto"
        timestamp updated_at "auto"
    }

    USER_PROFILES {
        varchar id PK "UUID, auto-generated"
        varchar user_id FK "unique, references users.id"
        varchar name "nullable"
        bigint phone_number "nullable"
        varchar avatar_url "nullable"
    }

    USER_ROLES {
        varchar user_id FK "references users.id"
        varchar role "ROLE_USER | ROLE_ADMIN | ROLE_MODERATOR"
    }

    CONVERSIONS {
        string _id PK "MongoDB ObjectId"
        string project_name "sanitized lowercase"
        string website_url "target URL"
        string app_title "display name"
        string icon_file "default: icon.ico"
        string current_version "default: 1.0.0"
        string status "DRAFT | READY | BUILDING | FAILED"
        string created_by "user email"
        datetime created_at "auto (MongoAuditing)"
        datetime updated_at "auto (MongoAuditing)"
    }

    REDIS_BLACKLIST {
        string key "blacklist:{refreshToken}"
        string value "user email"
        long ttl "remaining token TTL in ms"
    }

    USERS ||--o| USER_PROFILES : "has one"
    USERS ||--o{ USER_ROLES : "has many"
    USERS ||--o{ CONVERSIONS : "creates (via email)"
```

### Database Distribution

| Entity | Database | Engine |
|--------|----------|--------|
| `users`, `user_profiles`, `user_roles` | PostgreSQL (Neon) | JPA/Hibernate |
| `conversions` (collection) | MongoDB (Atlas) | Spring Data MongoDB |
| `blacklist:*` (keys) | Redis (Upstash) | Spring Data Redis |

---

## 5. Component Diagram — Frontend

```mermaid
graph TB
    subgraph App["App.tsx (Root)"]
        BR["BrowserRouter"]
        AP["AuthProvider (Context)"]
        NAV["Navbar"]
        ROUTES["AppRoutes"]
        FOOTER["Footer"]
    end

    subgraph Pages["Pages"]
        LP["LandingPage"]
        LI["LoginPage"]
        RE["RegisterPage"]
        DA["DashboardPage<br/>(ProtectedRoute)"]
    end

    subgraph LandingSections["Landing Page Sections"]
        HERO["Hero"]
        BENTO["BentoGrid"]
        SCROLL["ScrollText"]
    end

    subgraph Hooks["Hooks"]
        UA["useAuth()<br/>- user, isAuthenticated<br/>- login, register, logout"]
    end

    subgraph Services["API Services (services/api.ts)"]
        AUTH_API["authApi<br/>- register, login<br/>- refresh, logout<br/>- getProfile"]
        CONV_API["conversionApi<br/>- list, create, getById<br/>- update, remove, generate"]
        TOKEN_MGR["Token Manager<br/>- saveTokens, clearTokens<br/>- scheduleRefresh<br/>- axios interceptor"]
    end

    subgraph Types["TypeScript Types"]
        T_USER["User"]
        T_AUTH["AuthTokens"]
        T_CONV["ConversionProject"]
        T_ELEC["ElectronConfig"]
        T_REQ["LoginRequest, SignupRequest<br/>CreateConversionRequest"]
    end

    BR --> AP
    AP --> NAV
    AP --> ROUTES
    AP --> FOOTER

    ROUTES --> LP
    ROUTES --> LI
    ROUTES --> RE
    ROUTES --> DA

    LP --> HERO
    LP --> BENTO
    LP --> SCROLL

    LI --> UA
    RE --> UA
    DA --> UA
    DA --> CONV_API
    NAV --> UA

    UA --> AUTH_API
    AUTH_API --> TOKEN_MGR
    CONV_API --> TOKEN_MGR

    AUTH_API -.->|uses| T_USER
    AUTH_API -.->|uses| T_AUTH
    AUTH_API -.->|uses| T_REQ
    CONV_API -.->|uses| T_CONV
    CONV_API -.->|uses| T_ELEC

    style App fill:#1a1a2e,stroke:#2997ff,color:#fff
    style Pages fill:#1a1a2e,stroke:#bf5af2,color:#fff
    style LandingSections fill:#1a1a2e,stroke:#30d158,color:#fff
    style Hooks fill:#1a1a2e,stroke:#ff9f0a,color:#fff
    style Services fill:#1a1a2e,stroke:#ff453a,color:#fff
    style Types fill:#1a1a2e,stroke:#a1a1aa,color:#fff
```

### Frontend Architecture Notes

- **State Management**: React Context API via `AuthProvider` — no Redux/Zustand. Auth state (user, tokens) is the only global state.
- **Routing**: React Router v7 with a `ProtectedRoute` wrapper that redirects unauthenticated users to `/login`.
- **Token Refresh**: Automatic silent refresh scheduled 60 seconds before access token expiry. On failure, clears tokens and redirects to login.
- **API Layer**: Centralized Axios instance with request interceptor for JWT attachment. Vite dev server proxies `/user/*` and `/conversion/*` to the gateway at `:8080`.
- **Styling**: TailwindCSS with custom design tokens (glass morphism, gradients, custom colors). Dark-only theme with Apple-inspired aesthetic.
- **Animations**: Framer Motion for page transitions, scroll-linked text reveal, and micro-interactions.
