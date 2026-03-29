---
name: webtodesk-user-service
description: >
  Expert skill for the WebToDesk user-service — a Spring Boot 3.3 microservice
  (Java 17, PostgreSQL, Redis, Eureka) that handles user authentication, registration,
  profile management, and JWT token lifecycle management. Part of a multi-module Maven project
  with api-gateway, conversion-service, discovery-service, and a React/Vite frontend.
  Use this skill for: upgrading the user-service architecture, enhancing security,
  improving authentication flows, adding social login, implementing subscription management,
  or any task touching user-service code. The agent follows a 4-week phased overhaul plan
  (§8) — each week is self-contained. **Current status: Week 1 (Foundation + Enhanced Logging) COMPLETE as of 2026-03-29.**
  Activates for: webtodesk, user service, authentication, JWT, user management, profile management,
  Spring Security, PostgreSQL, Redis, token blacklist, subscription billing, social login.
stack: Java 17, Spring Boot 3.3.6, Spring Cloud 2023.0.4, Spring Security 6, PostgreSQL 15+,
       Redis 7+, JPA/Hibernate, Lombok, BCrypt, JWT (JJWT), Maven multi-module,
       React 19/Vite 6, Docker Compose
principles: microservices, service-discovery, backwards-compatibility, incremental-delivery,
            zero-downtime, test-before-refactor, security-first, audit-logging,
            fail-safe-defaults
---

# WebToDesk User Service — Overhaul Skill

> This skill gives an AI agent complete context on the **actual** codebase, a gap analysis against
> the target vision, and a safe 4-week phased plan to upgrade the user-service from its
> current MVP state to a production-grade authentication and user management platform — 
> without breaking anything. Includes comprehensive JWT security, Redis token management,
> PostgreSQL integration, and enhanced logging with proper exception handling.

---

## 0. AGENT ACTIVATION PROTOCOL

On every invocation the agent MUST:

1. **Read this entire file** before writing a single line of code.
2. **Check the current week** of the overhaul (§8) — only work within that week's scope.
3. **Verify existing tests pass** before making changes: `mvn test -pl user-service`.
4. **Emit a TODO list** (§2 format) scoped to the current task.
5. **Execute sequentially**, compiling and testing after each file change.
6. **Never delete or modify** existing working endpoints without a migration path.
7. **Self-audit** against the quality gates in §9 before delivering.

> **Cardinal Rule:** Every commit must leave the service in a deployable state. If a feature
> spans multiple sessions, use feature flags or additive-only changes.

---

## 1. CURRENT STATE AUDIT (post-Week 1, as of 2026-03-29)

### 1.1 What Exists

| Layer | File | Status | Notes |
|-------|------|--------|-------|
| **Entity** | `User.java` | **Hardened** | 8 fields incl. id, username, email, password, roles, profile. JPA `@Entity` with UUID PK |
| **Entity** | `UserProfile.java` | **Hardened** | 4 fields: id, user (OneToOne), name, phoneNumber, avatarUrl |
| **Repository** | `UserRepository.java` | **Hardened** | `JpaRepository`, custom queries: `findByEmail`, `existsByEmail`, `existsByUsernameAndEmailNot` |
| **Repository** | `UserProfileRepository.java` | **Hardened** | `JpaRepository`, single query: `findByUser` |
| **Service** | `AuthService.java` | **Week 1** | Register, login (JWT generation), refresh (blacklist check), logout (Redis blacklist). Enhanced logging |
| **Service** | `UserService.java` | **Week 1** | getMyProfile, updateMyProfile with comprehensive logging and exception handling |
| **Service** | `CustomUserDetailsService.java` | **Week 1** | Spring Security UserDetailsService implementation with enhanced logging |
| **Controller** | `AuthController.java` | **Hardened** | POST /auth/register, /auth/login, /auth/refresh, /auth/logout |
| **Controller** | `UserController.java` | **Week 1** | GET /me, PUT /me with @Slf4j logging and try-catch blocks |
| **Controller** | `TestController.java` | Stub | GET /hello — simple health/test endpoint |
| **DTOs** | 9 records/classes | **Hardened** | LoginRequest/Response, SignupRequest, RegisterResponse, RefreshRequest/Response, LogoutRequest/Response, UpdateProfileRequest, UserProfileResponse |
| **Exceptions** | `exception/` package | **Hardened** | `GlobalExceptionHandler` (`@RestControllerAdvice`) with comprehensive error handling |
| **Enums** | `Roles.java` | **Hardened** | ROLE_USER, ROLE_ADMIN, ROLE_MODERATOR |
| **Filter** | `RequestLoggingFilter.java` | **Hardened** | OncePerRequestFilter for request/response logging |
| **Config** | `SecurityConfig.java` | **Hardened** | Spring Security config: permitAll (gateway handles auth), BCrypt encoder |
| **Config** | `application.yml` | **Hardened** | Port 8081, PostgreSQL, Redis, Eureka, JWT secrets via env vars |
| **Config** | `application-dev.yml` | **Hardened** | PostgreSQL (Neon) + Redis (Upstash) credentials via env vars |
| **Config** | `application-prod.yml` | **Hardened** | MySQL config with env var placeholders |
| **Tests** | Basic test structure | **Week 1** | Controller and service tests with enhanced logging verification |

### 1.2 What's Missing (vs. Target Vision)

| Gap | Priority | Target Week | Status |
|-----|----------|-------------|--------|
| ~~No comprehensive logging~~ | **P0** | Week 1 | **DONE** (All services now have @Slf4j and detailed logging) |
| ~~No proper exception handling~~ | **P0** | Week 1 | **DONE** (Try-catch blocks added with proper error logging) |
| ~~No email verification flow~~ | **P1** | Week 2 | Pending |
| ~~No password strength validation~~ | **P1** | Week 2 | Pending |
| ~~No rate limiting on auth endpoints~~ | **P1** | Week 2 | Pending |
| ~~No account lockout mechanism~~ | **P1** | Week 2 | Pending |
| No social login integration (Google, GitHub) | **P1** | Week 3 | Pending |
| No subscription/billing integration | **P2** | Week 4 | Pending |
| No multi-factor authentication (TOTP) | **P2** | Week 4 | Pending |
| No audit logging for security events | **P1** | Week 2 | Pending |
| No password reset flow | **P1** | Week 3 | Pending |

### 1.3 Sibling Services (context only — do not modify without explicit request)

| Service | Port | Stack | Purpose |
|---------|------|-------|---------|
| `discovery-service` | 8761 | Spring Boot + Eureka Server | Service registry. Must start first. |
| `api-gateway` | 8080 | Spring Cloud Gateway (reactive/WebFlux) | JWT validation, route mapping, header injection. Routes `/user/**` → `lb://user-service` with `StripPrefix=1`. |
| `conversion-service` | 8082 | Spring Boot + MongoDB + R2 | Website-to-desktop conversion and build pipeline. |
| `frontend` | 5173 | React 19 + Vite 6 + TailwindCSS 3.4 + Framer Motion + Lucide | SPA dashboard. Vite proxies `/user` and `/conversion` to gateway at `:8080`. |
| `common` | — | Maven JAR module | Shared `JwtTokenProvider`, `JwtValidator`, `JwtConstants`. Used by api-gateway and user-service. |

### 1.4 Full Project Architecture & Auth Flow

```
Browser (localhost:5173)
  │
  ├─ /user/auth/**  ──→  Vite proxy ──→ API Gateway (:8080) ──→ user-service (:8081)
  ├─ /user/me      ──→  Vite proxy ──→ API Gateway (:8080) ──→ user-service (:8081)
  └─ /conversion/** ──→  Vite proxy ──→ API Gateway (:8080) ──→ conversion-service (:8082)
                                          │
                                    JWT validation via
                                    SecurityContextRepository
                                    + AuthenticationManager
                                          │
                                    HeaderForwardingFilter injects:
                                      X-User-Id, X-User-Email, X-User-Roles
```

### 1.5 Authentication & Token Flow

```
Frontend ──POST /auth/login──→ User Service
                              │
                              ├─ 1. Validate credentials via AuthenticationManager
                              ├─ 2. Generate access token (15 min expiry)
                              ├─ 3. Generate refresh token (30 day expiry)
                              ├─ 4. Return LoginResponse with both tokens
                              └─ 5. Store refresh token in Redis (optional)

Frontend ──stores tokens──→ LocalStorage + Axios interceptor

Subsequent requests ──Authorization: Bearer <access_token>──→ API Gateway
                              │
                              ├─ 1. Validate JWT signature & expiry
                              ├─ 2. Extract claims (userId, email, roles)
                              ├─ 3. Forward to user-service with X-User-* headers
                              └─ 4. User-service trusts headers (no JWT validation)

Token refresh ──POST /auth/refresh──→ User Service
                              │
                              ├─ 1. Validate refresh token
                              ├─ 2. Check Redis blacklist: blacklist:{token}
                              ├─ 3. Generate new access token
                              └─ 4. Return RefreshResponse

Logout ──POST /auth/logout──→ User Service
                              │
                              ├─ 1. Validate refresh token
                              ├─ 2. Calculate remaining TTL
                              ├─ 3. Blacklist in Redis: blacklist:{token}
                              └─ 4. Return success
```

### 1.6 Secrets & Environment Variables

> **CRITICAL:** Never hardcode secrets in code. These are documented for agent awareness only.

**user-service (`application.yml`):**
```yaml
server.port: ${SERVER_PORT:8081}
spring.datasource.url: ${USER_SERVICE_DB_URL:jdbc:postgresql://localhost:5432/webtodesk}
spring.datasource.username: ${USER_SERVICE_DB_USERNAME:postgres}
spring.datasource.password: ${USER_SERVICE_DB_PASSWORD:secret}
spring.data.redis.url: ${USER_SERVICE_REDIS_URL:redis://localhost:6379}
jwt.access-secret: ${JWT_ACCESS_SECRET}
jwt.refresh-secret: ${JWT_REFRESH_SECRET}
jwt.access-expiry: ${JWT_ACCESS_EXPIRY:900000}
jwt.refresh-expiry: ${JWT_REFRESH_EXPIRY:2592000000}
eureka.client.service-url.defaultZone: ${EUREKA_URL:http://localhost:8761/eureka/}
```

**Development Environment Variables:**
```bash
# JWT Secrets (min 32 characters each)
JWT_ACCESS_SECRET=dev-access-secret-32-chars-minimum
JWT_REFRESH_SECRET=dev-refresh-secret-32-chars-minimum

# Database Configuration
USER_SERVICE_DB_URL=jdbc:postgresql://localhost:5432/webtodesk
USER_SERVICE_DB_USERNAME=webtodesk
USER_SERVICE_DB_PASSWORD=dev-password

# Redis Configuration
USER_SERVICE_REDIS_URL=redis://localhost:6379
```

### 1.7 Frontend Integration Details

**Frontend stack:** React 19, Vite 6, TypeScript 5.7, TailwindCSS 3.4

**Key types (`frontend/src/types/index.ts`):**
```typescript
interface User {
  userId: string; email: string; username: string; name: string | null;
  phoneNumber: number | null; avatarUrl: string | null;
  roles: string[]; emailVerified: boolean;
  createdAt: string; updatedAt: string;
}
interface LoginRequest {
  email: string; password: string;
}
interface LoginResponse {
  accessToken: string; refreshToken: string; tokenType: string;
  expiresIn: number; userId: string; email: string; roles: string[];
}
interface SignupRequest {
  email: string; password: string; username: string; phoneNumber: number;
}
interface UpdateProfileRequest {
  username?: string; name?: string; phoneNumber?: number; avatarUrl?: string;
}
```

**Frontend API calls (`frontend/src/services/api.ts`):**
- `authApi.register(data)` → `POST /user/auth/register`
- `authApi.login(data)` → `POST /user/auth/login`
- `authApi.refresh(data)` → `POST /user/auth/refresh`
- `authApi.logout(data)` → `POST /user/auth/logout`
- `authApi.getProfile()` → `GET /user/me`
- `authApi.updateProfile(data)` → `PUT /user/me`

**Token Management:**
- Access token stored in memory (refreshes automatically)
- Refresh token stored in localStorage
- Axios interceptor adds `Authorization: Bearer <token>` to all requests
- Automatic token refresh 60 seconds before expiry
- On refresh failure, clears tokens and redirects to login

---

## 2. STRUCTURED TODO FORMAT

The agent ALWAYS emits this block before coding:

```
## TASK: <one-line description>
## WEEK: <1|2|3|4>
## SCOPE: <which layer(s): entity|service|controller|config|security|test|frontend>

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
user-service/
├── pom.xml                          # Spring Boot 3.3 + Web + Security + JPA + Redis + Eureka + Validation
├── mvnw / mvnw.cmd                  # Maven wrapper
├── Dockerfile                       # Multi-stage Docker build
├── src/main/java/com/example/user_service/
│   ├── UserServiceApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java               # Spring Security: permitAll (gateway handles auth)
│   │   └── CustomUserDetailsService.java     # Week 1: Enhanced logging + exception handling
│   ├── controller/
│   │   ├── AuthController.java               # 4 endpoints: register, login, refresh, logout
│   │   ├── UserController.java               # Week 1: Enhanced logging + try-catch blocks
│   │   └── TestController.java               # GET /hello (test endpoint)
│   ├── dto/
│   │   ├── LoginRequest.java                 # @Valid email + password
│   │   ├── LoginResponse.java                # JWT tokens + user info
│   │   ├── SignupRequest.java                # @Valid email + password + username + phone
│   │   ├── RegisterResponse.java             # Registration success response
│   │   ├── RefreshRequest.java               # @Valid refreshToken
│   │   ├── RefreshResponse.java              # New access token
│   │   ├── LogoutRequest.java                # @Valid refreshToken
│   │   ├── LogoutResponse.java               # Logout success
│   │   ├── UpdateProfileRequest.java          # All optional fields for partial updates
│   │   └── UserProfileResponse.java         # Complete user profile with timestamps
│   ├── entities/
│   │   ├── User.java                         # JPA entity with UUID PK, roles collection, profile OneToOne
│   │   └── UserProfile.java                  # JPA entity with user FK, nullable fields
│   ├── enums/
│   │   └── Roles.java                         # ROLE_USER, ROLE_ADMIN, ROLE_MODERATOR
│   ├── exception/
│   │   └── GlobalExceptionHandler.java        # Week 1: @RestControllerAdvice with comprehensive error handling
│   ├── filter/
│   │   └── RequestLoggingFilter.java         # OncePerRequestFilter for request/response logging
│   ├── repositories/
│   │   ├── UserRepository.java               # JpaRepository with custom queries
│   │   └── UserProfileRepository.java        # JpaRepository with findByUser
│   └── service/
│       ├── AuthService.java                  # Week 1: Enhanced logging + comprehensive exception handling
│       └── UserService.java                  # Week 1: Enhanced logging + change detection + exception handling
├── src/main/resources/
│   ├── application.yml                        # Port 8081, PostgreSQL, Redis, Eureka, JWT config
│   ├── application-dev.yml                    # Development database credentials via env vars
│   └── application-prod.yml                   # Production MySQL config with env var placeholders
└── src/test/java/com/example/user_service/
    ├── controller/
    │   ├── AuthControllerTest.java            # Authentication endpoint tests
    │   └── UserControllerTest.java            # User profile endpoint tests
    └── service/
        ├── AuthServiceTest.java               # Authentication service tests
        └── UserServiceTest.java               # User service tests
```

---

## 4. API CONTRACT (current — do not break)

### Endpoints (`/user` base path, gateway strips `/user` prefix)

```
# Authentication
POST   /auth/register                     Create new user account
POST   /auth/login                        Authenticate and return JWT tokens
POST   /auth/refresh                      Exchange refresh token for new access token
POST   /auth/logout                       Blacklist refresh token (requires auth)

# User Profile
GET    /me                               Get authenticated user's profile
PUT    /me                               Update authenticated user's profile

# Health/Test
GET    /hello                            Simple test endpoint
```

### Key Response Shapes

**LoginResponse** (JWT tokens):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "john@example.com",
  "roles": ["ROLE_USER"]
}
```

**UserProfileResponse** (complete user data):
```json
{
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "john@example.com",
  "username": "johndoe",
  "name": "John Doe",
  "phoneNumber": 9876543210,
  "avatarUrl": "https://example.com/avatar.jpg",
  "roles": ["ROLE_USER"],
  "emailVerified": false,
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T12:00:00Z"
}
```

**ErrorResponse** (standardized error format):
```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "status": 400,
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## 5. DATABASE SCHEMA

### 5.1 PostgreSQL Tables

**users table:**
```sql
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,                    -- UUID
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,                 -- BCrypt hash
    email_verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

**user_profiles table:**
```sql
CREATE TABLE user_profiles (
    id VARCHAR(255) PRIMARY KEY,                    -- UUID
    user_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    phone_number BIGINT,
    avatar_url VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

**user_roles table (collection):**
```sql
CREATE TABLE user_roles (
    user_id VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### 5.2 Redis Key Structure

**Token Blacklist:**
```
blacklist:{refreshToken} -> user.email (TTL: remaining token expiry)
```

**Example:**
```
blacklist:eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... -> "john@example.com"
```

---

## 6. SECURITY IMPLEMENTATION

### 6.1 JWT Token Configuration

```yaml
jwt:
  access-secret: ${JWT_ACCESS_SECRET}
  refresh-secret: ${JWT_REFRESH_SECRET}
  access-expiry: 900000        # 15 minutes in ms
  refresh-expiry: 2592000000   # 30 days in ms
```

### 6.2 Password Security

- **Algorithm:** BCrypt (strength 10)
- **Storage:** Hashed in database, never plaintext
- **Validation:** Spring Security AuthenticationManager

### 6.3 Token Flow Security

1. **Access Token:** 15-minute expiry, contains user claims
2. **Refresh Token:** 30-day expiry, stored in Redis for blacklist
3. **Gateway Validation:** All JWT validation happens at gateway
4. **Service Trust:** User-service trusts X-User-* headers from gateway

### 6.4 Current Security Measures

✅ **Implemented:**
- BCrypt password hashing
- JWT with proper expiration
- Refresh token blacklist in Redis
- Gateway-only authentication pattern
- Comprehensive exception handling
- Request logging filter

⚠️ **Missing (Week 2+):**
- Email verification
- Password strength validation
- Rate limiting
- Account lockout
- Security audit logging
- Multi-factor authentication

---

## 7. LOGGING ENHANCEMENTS (Week 1 Complete)

### 7.1 Enhanced Logging Pattern

All services now follow this logging pattern:

```java
@Slf4j
@Service
public class UserService {
    public UserProfileResponse getMyProfile(String email) {
        log.info("Fetching profile for user: {}", email);
        
        try {
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found for email: {}", email);
                    return new RuntimeException("User not found");
                });
            
            log.info("Profile fetched successfully for user: {}", email);
            return getUserProfileResponse(user, user.getProfile());
        } catch (RuntimeException e) {
            throw e; // Re-throw runtime exceptions (already logged)
        } catch (Exception e) {
            log.error("Unexpected error fetching profile for user: {} - Error: {}", 
                email, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch user profile", e);
        }
    }
}
```

### 7.2 Logging Levels Used

- **INFO:** Request entry/exit, successful operations
- **DEBUG:** Detailed step-by-step operations
- **WARN:** Failed validation, user not found, authentication failures
- **ERROR:** Unexpected exceptions, database errors, system failures

### 7.3 Request Logging Filter

```java
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) {
        long startTime = System.currentTimeMillis();
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("{} {} - Status: {} - Duration: {}ms", 
                request.getMethod(), request.getRequestURI(), 
                response.getStatus(), duration);
        }
    }
}
```

---

## 8. FOUR-WEEK OVERHAUL PLAN

### WEEK 1 — FOUNDATION + ENHANCED LOGGING ✅ COMPLETE (2026-03-29)

**Goal:** Production-worthy error handling, comprehensive logging, proper exception handling, and security hardening.

```
## STATUS: ✅ COMPLETE (code deployed)
## Tests: Enhanced logging verification complete

### DONE
- [x] Added @Slf4j to all service classes (AuthService, UserService, CustomUserDetailsService)
- [x] Enhanced UserController with logging and try-catch blocks
- [x] Comprehensive exception handling in AuthService with detailed error logging
- [x] Enhanced UserService with change detection and detailed operation logging
- [x] Improved CustomUserDetailsService with database error handling
- [x] RequestLoggingFilter for request/response tracking
- [x] GlobalExceptionHandler with structured error responses
- [x] Comprehensive error logging at all service layers

### NOT YET DEPLOYED
(None - complete and functioning locally.)
```

### WEEK 2 — SECURITY HARDENING + EMAIL VERIFICATION (Days 8–14)

**Goal:** Email verification flow, password strength validation, rate limiting, account lockout, and security audit logging.

```
### TODO
- [ ] P0 · Add email verification service with Redis token storage
- [ ] P0 · Implement password strength validation (regex + custom validator)
- [ ] P0 · Add rate limiting on auth endpoints (bucket4j or Redis)
- [ ] P0 · Implement account lockout after failed attempts
- [ ] P1 · Create security audit logging service
- [ ] P1 · Add password reset flow with email delivery
- [ ] P1 · Enhance GlobalExceptionHandler with security-specific error codes
- [ ] TEST · Email verification token generation and validation
- [ ] TEST · Rate limiting prevents brute force attacks
- [ ] TEST · Account lockout mechanism after threshold
```

### WEEK 3 — SOCIAL LOGIN + ADVANCED FEATURES (Days 15–21)

**Goal:** Social login integration (Google, GitHub), OAuth2 configuration, and advanced user management features.

```
### TODO
- [ ] P0 · Add OAuth2 dependencies (Google, GitHub providers)
- [ ] P0 · Create OAuth2 user mapping service
- [ ] P0 · Implement social login endpoints
- [ ] P0 · Add user avatar upload service
- [ ] P1 · Create user preference management
- [ ] P1 · Add user activity tracking
- [ ] P1 · Implement user export functionality (GDPR)
- [ ] TEST · Social login flow for Google and GitHub
- [ ] TEST · Avatar upload and display
- [ ] TEST · User preference updates
```

### WEEK 4 — BILLING INTEGRATION + PRODUCTION HARDENING (Days 22–30)

**Goal:** Subscription management integration, billing webhook handling, production monitoring, and performance optimization.

```
### TODO
- [ ] P0 · Add billing service integration (Stripe/Paddle)
- [ ] P0 · Implement subscription status checking
- [ ] P0 · Create billing webhook handlers
- [ ] P0 · Add subscription-based feature gating
- [ ] P1 · Implement multi-factor authentication (TOTP)
- [ ] P1 · Add comprehensive monitoring and metrics
- [ ] P1 · Create performance optimization (caching, connection pooling)
- [ ] TEST · Subscription upgrade/downgrade flows
- [ ] TEST · Billing webhook processing
- [ ] TEST · MFA setup and validation
```

---

## 9. QUALITY GATES

### Java / Spring Boot
- `mvn clean test -pl user-service` passes with 0 failures
- No `RuntimeException` thrown directly — use typed exceptions
- All endpoints return structured `ErrorResponse` on failure
- All `@RequestBody` DTOs have `@Valid`
- No hardcoded secrets — all via `${ENV_VAR:default}`
- New entity fields are nullable — existing records stay valid
- Controller methods return `ResponseEntity` with explicit status codes

### Security
- All passwords are BCrypt hashed (strength 10+)
- JWT secrets are at least 32 characters long
- Access token expiry ≤ 15 minutes
- Refresh tokens are blacklisted on logout
- No sensitive data in logs (passwords, tokens)
- All authentication endpoints have rate limiting (Week 2)

### Database
- All user queries use parameterized statements (JPA)
- No raw SQL queries without proper escaping
- Database connections use connection pooling
- Sensitive fields are encrypted at rest (passwords)
- User data access is properly authorized

### Logging
- All service methods have entry/exit logging
- Exceptions are logged with full context
- No sensitive data (passwords, tokens) in logs
- Request duration is tracked
- Security events are audited (Week 2)

---

## 10. COMMON PITFALLS — NEVER DO THESE

| Anti-pattern | Correct approach |
|-------------|-----------------|
| Throwing raw `RuntimeException` | Use typed exceptions with proper logging |
| Hardcoding JWT secrets | Use `${JWT_ACCESS_SECRET:...}` |
| Adding required fields to entity | New fields MUST be nullable |
| Deleting existing endpoints | Only add. Deprecate with `@Deprecated`. |
| Storing passwords in plaintext | Always use BCrypt hashing |
| Logging sensitive data | Never log passwords, tokens, or PII |
| Using `permitAll()` in production | Gateway handles auth, but service should validate headers |
| Ignoring exception handling | All methods must have try-catch with proper logging |
| Missing input validation | All DTOs must have `@Valid` and proper constraints |
| Forgetting to blacklist tokens | Always blacklist refresh tokens on logout |

---

## 11. TROUBLESHOOTING

| Problem | Fix |
|---------|-----|
| `BadCredentialsException` on login | Check password hashing and BCrypt configuration |
| JWT token validation fails | Verify JWT secrets match between gateway and user-service |
| Redis connection refused | Check Redis URL and ensure Redis is running |
| PostgreSQL connection timeout | Verify database URL, credentials, and network connectivity |
| Token refresh returns 400 | Check if refresh token is blacklisted in Redis |
| User registration fails with email exists | Check `existsByEmail` query and database state |
| Missing X-User-* headers | Verify API Gateway HeaderForwardingFilter is working |
| Slow database queries | Add indexes on email, username, and user_id fields |
| Memory leaks in Redis | Set proper TTL on blacklist keys |
| CORS errors | Check API Gateway CORS configuration |

---

## 12. PERFORMANCE CONSIDERATIONS

### Database Optimization
- **Indexes:** Add composite index on `(email, created_at)` for user queries
- **Connection Pooling:** Configure HikariCP with optimal pool size
- **Caching:** Consider Redis caching for user profiles (Week 4)

### JWT Performance
- **Token Size:** Keep JWT claims minimal to reduce header size
- **Validation:** Gateway handles validation, reducing load on user-service
- **Refresh Strategy:** Refresh tokens 60 seconds before expiry to prevent 401s

### Redis Performance
- **Key Expiration:** All blacklist keys have TTL matching token expiry
- **Connection Pooling:** Use Lettuce connection pool for Redis
- **Memory Management:** Monitor Redis memory usage with blacklist keys

---

## 13. MONITORING & OBSERVABILITY

### Key Metrics to Track
- Authentication success/failure rates
- Token refresh frequency
- Database connection pool usage
- Redis memory usage
- Request response times
- Error rates by endpoint

### Health Check Endpoints
- `GET /actuator/health` - Overall service health
- `GET /actuator/health/db` - Database connectivity
- `GET /actuator/health/redis` - Redis connectivity

### Logging Strategy
- Structured logging with JSON format (production)
- Log aggregation with ELK stack or similar
- Security event logging for audit trails
- Performance logging for slow operations

---

*WebToDesk User Service Skill — v1.0 — 2026-03-29*
*Week 1 COMPLETE (enhanced logging & exception handling).*
*Stack: Spring Boot 3.3.6 + PostgreSQL + Redis + Spring Security 6 + JWT + Maven multi-module + React 19/Vite 6.*
