# WebToDesk — File Structure

Complete annotated file tree of the entire project repository.

```
/
├── .gitignore                                          # Git ignore rules (Maven, Node, IDE, Docker)
├── .vscode/                                            # VS Code workspace settings
├── docker-compose.yml                                  # Docker Compose — 4 services + bridge network
├── pom.xml                                             # Maven parent POM — multi-module project
├── start-all.ps1                                       # PowerShell script to start all 5 services locally
├── README.md                                           # Project overview and setup guide
├── ARCHITECTURE.md                                     # Architecture docs with Mermaid diagrams
├── FILE_STRUCTURE.md                                   # This file
├── DB_SCHEMA.md                                        # Database schema documentation
├── API_REFERENCE.md                                    # Complete API endpoint reference
├── SUBSCRIPTION_AND_BILLING.md                         # Billing model documentation
├── DEPLOYMENT.md                                       # Production deployment guide
├── IMPROVEMENTS.md                                     # Prioritized improvement recommendations
│
├── common/                                             # Shared Java library (Maven module)
│   ├── pom.xml                                         # Dependencies: jjwt, jakarta.persistence, lombok
│   └── src/main/
│       ├── java/com/example/common/
│       │   ├── dto/
│       │   │   └── ErrorResponse.java                  # Standardized error response record (error, message, status, timestamp)
│       │   └── security/
│       │       ├── JwtConstants.java                   # Static constants: header name, prefix, token expiry durations
│       │       ├── JwtTokenProvider.java               # Generates access & refresh JWTs (HMAC-SHA signing)
│       │       └── JwtValidator.java                   # Validates & parses JWTs, enforces token type (access vs refresh)
│       └── resources/
│           └── application.yml                         # ⚠️ JWT secrets (HARDCODED — must externalize)
│
├── discovery-service/                                  # Netflix Eureka Server (service registry)
│   ├── pom.xml                                         # Dependencies: spring-cloud-starter-netflix-eureka-server
│   └── src/main/
│       ├── java/com/example/discovery_service/
│       │   └── DiscoveryServiceApplication.java        # @EnableEurekaServer main class
│       └── resources/
│           └── application.yml                         # Port 8761, self-registration disabled
│   ⚠️ MISSING: Dockerfile
│
├── api-gateway/                                        # Spring Cloud Gateway (reactive, WebFlux)
│   ├── .gitattributes                                  # Git line-ending config
│   ├── .mvn/                                           # Maven wrapper config
│   ├── Dockerfile                                      # Multi-stage: maven build → temurin-17 JRE
│   ├── HELP.md                                         # Spring Initializr reference links
│   ├── mvnw / mvnw.cmd                                 # Maven wrapper scripts (Unix / Windows)
│   ├── pom.xml                                         # Dependencies: gateway, security, eureka-client, common module
│   └── src/main/
│       ├── java/com/example/api_gateway/
│       │   ├── ApiGatewayApplication.java              # @EnableDiscoveryClient, scans com.example + com.example.common
│       │   ├── config/
│       │   │   ├── GatewaySecurityConfig.java          # WebFlux security chain: CSRF off, CORS config, path matchers
│       │   │   └── RouterValidator.java                # Lists open (unauthenticated) API endpoints for validation
│       │   ├── filter/
│       │   │   └── HeaderForwardingFilter.java         # GlobalFilter: extracts JWT claims → X-User-Id/Email/Roles headers
│       │   └── security/
│       │       ├── AuthenticationManager.java          # ReactiveAuthenticationManager: validates access token, builds auth
│       │       └── SecurityContextRepository.java      # Extracts Bearer token from Authorization header, delegates to AuthMgr
│       └── resources/
│           └── application.yml                         # Port 8080, route config (/user/** → user-service, /conversion/** → conversion-service)
│
├── user-service/                                       # User management & authentication service
│   ├── .gitattributes                                  # Git line-ending config
│   ├── .mvn/                                           # Maven wrapper config
│   ├── Dockerfile                                      # Multi-stage: maven build → temurin-17 JRE
│   ├── HELP.md                                         # Spring Initializr reference links
│   ├── mvnw / mvnw.cmd                                 # Maven wrapper scripts
│   ├── pom.xml                                         # Dependencies: web, JPA, security, validation, redis, eureka, postgresql, common
│   └── src/main/
│       ├── java/com/example/user_service/
│       │   ├── UserServiceApplication.java             # @EnableDiscoveryClient, scans com.example + com.example.common
│       │   ├── config/
│       │   │   ├── CustomUserDetailsService.java       # Loads user by email for Spring Security authentication
│       │   │   └── SecurityConfig.java                 # Servlet security: everything permitAll (gateway handles auth), BCrypt encoder
│       │   ├── controller/
│       │   │   ├── AuthController.java                 # POST /auth/register, /auth/login, /auth/refresh, /auth/logout
│       │   │   ├── UserController.java                 # GET /me, PUT /me — profile management
│       │   │   └── TestController.java                 # GET /hello — simple health/test endpoint
│       │   ├── dto/
│       │   │   ├── LoginRequest.java                   # Record: email (validated), password (validated)
│       │   │   ├── LoginResponse.java                  # Record: accessToken, refreshToken, tokenType, expiresIn, userId, email, roles
│       │   │   ├── SignupRequest.java                  # Record: email, password (min 6), phoneNumber, username
│       │   │   ├── RegisterResponse.java               # Record: message, email, userId, createdAt
│       │   │   ├── RefreshRequest.java                 # Record: refreshToken (validated)
│       │   │   ├── RefreshResponse.java                # Record: accessToken, tokenType, expiresIn
│       │   │   ├── LogoutRequest.java                  # Record: refreshToken (validated)
│       │   │   ├── LogoutResponse.java                 # Record: message
│       │   │   ├── UpdateProfileRequest.java           # Record: username, name, phoneNumber, avatarUrl (all optional)
│       │   │   └── UserProfileResponse.java            # Record: full user profile with roles and timestamps
│       │   ├── entities/
│       │   │   ├── User.java                           # JPA @Entity: users table — id (UUID), username, email, password, roles, profile
│       │   │   └── UserProfile.java                    # JPA @Entity: user_profiles table — id (UUID), name, phoneNumber, avatarUrl
│       │   ├── enums/
│       │   │   └── Roles.java                          # Enum: ROLE_USER, ROLE_ADMIN, ROLE_MODERATOR
│       │   ├── exception/
│       │   │   └── GlobalExceptionHandler.java         # @RestControllerAdvice: handles BadCredentials, NotFound, Validation, Runtime, general
│       │   ├── filter/
│       │   │   └── RequestLoggingFilter.java           # OncePerRequestFilter: logs method, URI, IP, status, duration
│       │   ├── repositories/
│       │   │   ├── UserRepository.java                 # JpaRepository: findByEmail, existsByEmail, existsByUsernameAndEmailNot
│       │   │   └── UserProfileRepository.java          # JpaRepository: findByUser
│       │   └── service/
│       │       ├── AuthService.java                    # Register, login (with JWT generation), refresh (with blacklist check), logout (blacklist in Redis)
│       │       └── UserService.java                    # getMyProfile, updateMyProfile — maps entities to DTOs
│       └── resources/
│           ├── application.yml                         # Port 8081, JWT secrets, active profile: dev, Eureka URL
│           ├── application-dev.yml                     # ⚠️ PostgreSQL (Neon) + Redis (Upstash) credentials (HARDCODED)
│           └── application-prod.yml                    # MySQL config with env var placeholder for password
│
├── conversion-service/                                 # Website-to-desktop conversion engine
│   ├── pom.xml                                         # Dependencies: web, mongodb, security, validation, eureka, lombok
│   └── src/main/
│       ├── java/com/example/conversion_service/
│       │   ├── ConversionServiceApplication.java       # @EnableDiscoveryClient main class
│       │   ├── config/
│       │   │   └── ConversionSecurityConfig.java       # Servlet security: everything permitAll, @EnableMongoAuditing
│       │   ├── controller/
│       │   │   └── ConversionController.java           # CRUD + generate: POST, GET, GET/{id}, PUT/{id}, DELETE/{id}, POST/{id}/generate
│       │   ├── dto/
│       │   │   ├── CreateConversionRequest.java        # Record: projectName, websiteUrl (@URL), appTitle — all validated
│       │   │   ├── UpdateConversionRequest.java        # Record: all fields optional (partial update)
│       │   │   ├── ConversionResponse.java             # Record: full project data with static from() mapper
│       │   │   └── ElectronConfigResponse.java         # Record: projectName, appTitle, websiteUrl, files (Map<String,String>)
│       │   ├── entity/
│       │   │   └── ConversionProject.java              # MongoDB @Document: conversions collection — with ConversionStatus enum
│       │   ├── repository/
│       │   │   └── ConversionRepository.java           # MongoRepository: findByCreatedByOrderByCreatedAtDesc
│       │   └── service/
│       │       └── ConversionService.java              # CRUD + Electron project generation (config.js, main.js, preload.js, package.json)
│       └── resources/
│           └── application.yml                         # Port 8082, ⚠️ MongoDB URI (HARDCODED), Eureka URL
│   ⚠️ MISSING: Dockerfile
│
└── frontend/                                           # React TypeScript SPA
    ├── index.html                                      # HTML entry point (dark theme, Inter font, Vite module script)
    ├── package.json                                    # React 19, react-router-dom 7, framer-motion, lucide-react, axios
    ├── package-lock.json                               # NPM lockfile
    ├── postcss.config.js                               # PostCSS: tailwindcss + autoprefixer
    ├── tailwind.config.js                              # TailwindCSS: custom colors (surface/accent), glass animations, Inter font
    ├── tsconfig.json                                   # TypeScript: ES2020, strict, @/* path alias
    ├── vite.config.ts                                  # Vite: React plugin, port 5173, proxy /user + /conversion → :8080
    └── src/
        ├── main.tsx                                    # React root: StrictMode + createRoot
        ├── App.tsx                                     # BrowserRouter → AuthProvider → Navbar + Routes + Footer
        ├── index.css                                   # TailwindCSS base + component layer (glass, gradients, buttons, inputs)
        ├── types/
        │   └── index.ts                                # TypeScript interfaces: User, AuthTokens, ConversionProject, ElectronConfig, requests
        ├── services/
        │   └── api.ts                                  # Axios instance + token management + authApi + conversionApi
        ├── hooks/
        │   └── useAuth.tsx                             # AuthContext + AuthProvider: user state, login/register/logout, auto-refresh
        ├── pages/
        │   ├── LandingPage.tsx                         # Marketing page: Hero + BentoGrid + ScrollText sections
        │   ├── LoginPage.tsx                           # Email + password form with error handling and loading state
        │   ├── RegisterPage.tsx                        # Username + email + password + phone form with success redirect
        │   └── DashboardPage.tsx                       # Protected: project CRUD, generate modal, file download
        └── components/
            ├── layout/
            │   ├── Navbar.tsx                          # Fixed nav: logo, links, auth-aware menu, mobile hamburger
            │   └── Footer.tsx                          # Simple footer with copyright
            └── sections/
                ├── Hero.tsx                            # Landing hero: headline, subheadline, CTAs, browser mockup
                ├── BentoGrid.tsx                       # Feature cards grid (6 features with icons)
                └── ScrollText.tsx                      # Scroll-linked word-by-word text reveal animation
```

## Missing Files & Directories

| Expected | Status | Notes |
|----------|--------|-------|
| `.env.example` | ⚠️ MISSING | No environment variable template for developers |
| `discovery-service/Dockerfile` | ⚠️ MISSING | Cannot containerize discovery service |
| `conversion-service/Dockerfile` | ⚠️ MISSING | Cannot containerize conversion service |
| `**/src/test/` | ⚠️ MISSING | Zero test files across all modules |
| `.github/workflows/` | ⚠️ MISSING | No CI/CD pipeline configuration |
| `frontend/.env.example` | ⚠️ MISSING | No frontend env template |
| `nginx.conf` | ⚠️ MISSING | No reverse proxy config for production |
| `k8s/` | ⚠️ MISSING | No Kubernetes manifests |
| `db/migrations/` | ⚠️ MISSING | No Flyway/Liquibase migration scripts (using hibernate ddl-auto) |
