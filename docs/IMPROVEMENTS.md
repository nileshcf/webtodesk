# WebToDesk — Improvement Recommendations

Prioritized list of recommended improvements identified during the codebase audit.

---

## Priority Legend

| Priority | Meaning |
| --- | --- |
| **P1 — Critical** | Security vulnerabilities, data loss risks, billing failures |
| **P2 — High** | Missing features that block production readiness |
| **P3 — Medium** | UX improvements, performance, observability |
| **P4 — Low** | Nice-to-haves, refactors, tech debt cleanup |

## Effort Legend

| Size | Meaning |
| --- | --- |
| **S** | < 1 day |
| **M** | 1–3 days |
| **L** | 3–7 days |
| **XL** | 1–2 weeks |

---

## P1 — Critical

### 1.1 Hardcoded Secrets in Source Code

**Problem**: JWT secrets, PostgreSQL credentials (Neon), MongoDB URI (Atlas), and Redis URL (Upstash) are hardcoded in committed YAML files:

- `common/src/main/resources/application.yml` — JWT secrets
- `user-service/src/main/resources/application-dev.yml` — Neon PostgreSQL password, Upstash Redis URL
- `conversion-service/src/main/resources/application.yml` — MongoDB Atlas URI with credentials
- `api-gateway/src/main/resources/application.yml` — JWT secrets duplicated

**Impact**: Anyone with repository access has full credentials to all databases and can forge JWT tokens. If the repo is public, all user data is immediately compromised.

**Solution**:

1. Rotate all secrets immediately (generate new JWT keys, change all DB passwords)
2. Replace hardcoded values with environment variable placeholders:

```yaml
jwt:
  access-secret: ${JWT_ACCESS_SECRET}
  refresh-secret: ${JWT_REFRESH_SECRET}

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  data:
    redis:
      url: ${SPRING_DATA_REDIS_URL}
    mongodb:
      uri: ${SPRING_DATA_MONGODB_URI}
```

3. Create a `.env.example` with placeholder values
4. Add `*.yml` secret patterns to `.gitignore` or use `application-local.yml` (gitignored)

**Effort**: S

---

### 1.2 No Authorization on Conversion Endpoints (IDOR Vulnerability)

**Problem**: The conversion service's `getById`, `update`, `delete`, and `generate` endpoints accept any project ID without verifying ownership. Any authenticated user can read, modify, delete, or generate files for any other user's project simply by guessing/iterating MongoDB ObjectIDs.

**Impact**: Complete cross-user data access. Users can steal conversion configurations, delete other users' projects, or access proprietary website URLs.

**Affected code**: `ConversionController.java` — `getById`, `update`, `delete`, `generate` methods do not pass `X-User-Email` or check `createdBy`.

**Solution**: Add ownership verification to every single-resource endpoint:

```java
@GetMapping("/{id}")
public ResponseEntity<ConversionResponse> getById(
        @PathVariable String id,
        @RequestHeader("X-User-Email") String userEmail) {
    ConversionProject project = findOrThrow(id);
    if (!project.getCreatedBy().equals(userEmail)) {
        throw new AccessDeniedException("Not authorized to access this project");
    }
    return ResponseEntity.ok(ConversionResponse.from(project));
}
```

Apply the same pattern to `update`, `delete`, and `generate`.

**Effort**: S

---

### 1.3 No Input Sanitization for Website URLs

**Problem**: The `websiteUrl` field in `CreateConversionRequest` only validates URL format (`@URL`) but does not prevent:

- Internal network URLs (SSRF): `http://169.254.169.254/latest/meta-data/` (AWS metadata)
- Local file access: `file:///etc/passwd`
- Internal service URLs: `http://user-service:8081/hello`
- JavaScript injection: `javascript:alert(1)`
- Extremely long URLs that could cause memory issues

**Impact**: Server-Side Request Forgery (SSRF) if the conversion service ever fetches the URL server-side. Even without fetching, the URL is embedded into generated Electron `config.js` without escaping, enabling potential code injection in the generated desktop app.

**Solution**:

1. Validate URL scheme is `http` or `https` only
2. Block private/internal IP ranges (10.x, 172.16-31.x, 192.168.x, 127.x, 169.254.x)
3. Escape values when embedding in generated JavaScript files (use JSON string encoding)
4. Set a maximum URL length (e.g., 2048 characters)

```java
private void validateWebsiteUrl(String url) {
    URI uri = URI.create(url);
    if (!List.of("http", "https").contains(uri.getScheme())) {
        throw new IllegalArgumentException("Only http/https URLs are allowed");
    }
    InetAddress addr = InetAddress.getByName(uri.getHost());
    if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
        throw new IllegalArgumentException("Internal URLs are not allowed");
    }
}
```

**Effort**: S

---

### 1.4 No HTTPS Enforcement / Insecure Cookie Configuration

**Problem**: No TLS is configured anywhere. The frontend stores JWT tokens in `localStorage` (vulnerable to XSS). No `Secure`, `HttpOnly`, or `SameSite` cookie attributes are used since tokens are managed client-side.

**Impact**: Tokens can be stolen via XSS attacks. Man-in-the-middle attacks possible without TLS.

**Solution**:

1. Short-term: Add TLS termination via nginx/Cloudflare (see DEPLOYMENT.md)
2. Long-term: Move refresh tokens to `HttpOnly` + `Secure` + `SameSite=Strict` cookies instead of localStorage
3. Implement Content Security Policy headers

**Effort**: M

---

## P2 — High

### 2.1 No Payment / Subscription System

**Problem**: The product is described as a PAID service, but there is zero billing infrastructure — no payment gateway integration, no subscription entities, no plan enforcement, no usage limits.

**Impact**: Cannot monetize. All features are free and unlimited for all users.

**Solution**: See SUBSCRIPTION_AND_BILLING.md for the complete implementation plan. Summary:

1. Add `Subscription` entity to PostgreSQL
2. Create `payment-service` with Stripe integration
3. Implement checkout, webhooks, plan enforcement
4. Add pricing page and billing UI to frontend

**Effort**: XL

---

### 2.2 No Server-Side .exe Build Pipeline

**Problem**: The "conversion" only generates Electron source files returned as JSON. Users must manually run `npm install && npm run dist` to build the actual desktop app. This is the core value proposition of the product and it's incomplete.

**Impact**: Product is not usable by non-technical users. The "convert website to desktop app" promise is only half-fulfilled.

**Solution**:

1. Add a job queue (RabbitMQ/Redis) for build requests
2. Create a build worker service that:
   - Writes generated files to a temp directory
   - Runs `npm install && npx electron-builder` in a Docker container
   - Uploads the built artifact (.exe/.dmg/.AppImage) to S3/GCS
   - Updates project status (BUILDING → READY or FAILED)
   - Generates a time-limited download URL
3. Add status polling or WebSocket notifications to the frontend
4. Add download link to the dashboard

**Effort**: XL

---

### 2.3 No Tests

**Problem**: Zero test files exist across the entire codebase — no unit tests, no integration tests, no end-to-end tests, no test configurations.

**Impact**: No confidence in code correctness. Any change can introduce regressions undetected. Blocks CI/CD pipeline implementation.

**Solution**:

1. **User Service Unit Tests** (M): Test AuthService, UserService with mocked repositories
2. **User Service Integration Tests** (M): Test controllers with `@SpringBootTest` + Testcontainers
3. **Conversion Service Unit Tests** (S): Test ConversionService and generated file content
4. **API Gateway Tests** (S): Test JWT filter, routing, CORS
5. **Frontend Tests** (M): Add Vitest + React Testing Library for component tests
6. **E2E Tests** (L): Add Playwright for critical flows (register → login → create → generate → download)

**Effort**: L (to reach reasonable coverage)

---

### 2.4 No Database Migrations

**Problem**: PostgreSQL schema is managed by `hibernate.ddl-auto: update` in dev. No Flyway or Liquibase migrations exist. The prod profile uses `validate` which means the first production deployment will fail because no schema exists.

**Impact**: Cannot deploy to a fresh production database. Schema changes are unreviewable and irreversible. Risk of accidental data loss from Hibernate auto-DDL.

**Solution**:

1. Add Flyway dependency to user-service
2. Create initial migration `V1__init_schema.sql` from current entity definitions
3. Change dev profile to `ddl-auto: validate`
4. All future schema changes go through migration scripts

See DEPLOYMENT.md §6 for the complete migration SQL.

**Effort**: S

---

### 2.5 Missing Dockerfiles

**Problem**: `discovery-service` and `conversion-service` have no Dockerfiles. The `docker-compose.yml` references them but builds will fail. No frontend Dockerfile exists.

**Impact**: Cannot use Docker Compose to run the full stack. Blocks containerized deployment.

**Solution**: Create Dockerfiles for all three missing services (templates provided in DEPLOYMENT.md).

**Effort**: S

---

### 2.6 No Rate Limiting

**Problem**: No rate limiting on any endpoint. The login endpoint is especially vulnerable to brute-force attacks.

**Impact**: Credential stuffing attacks, resource exhaustion, API abuse.

**Solution**:

1. Add Spring Cloud Gateway rate limiting with Redis:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service-route
          uri: lb://user-service
          predicates:
            - Path=/user/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@ipKeyResolver}"
```

2. Add stricter limits on auth endpoints (e.g., 5 login attempts per minute per IP)

**Effort**: S

---

### 2.7 Email Verification Not Implemented

**Problem**: The `User` entity has an `emailVerified` field (default `false`) but no verification flow exists — no email sending, no verification token generation, no verification endpoint.

**Impact**: Users can register with any email (including emails they don't own). No way to communicate with users (password reset, billing notifications).

**Solution**:

1. Add email service (Spring Mail + SendGrid/SES)
2. Generate verification token on registration
3. Send verification email with link
4. Add `GET /auth/verify?token=xxx` endpoint
5. Optionally: block login until email is verified

**Effort**: M

---

### 2.8 Password Reset Not Implemented

**Problem**: `RouterValidator` references `/user/auth/forgot-password` and `/user/auth/reset-password` as open endpoints, but no controller methods or service logic exists.

**Impact**: Users who forget their password are permanently locked out.

**Solution**:

1. Add `POST /auth/forgot-password` — generates reset token, sends email
2. Add `POST /auth/reset-password` — validates token, updates password
3. Store reset tokens in Redis with short TTL (15 minutes)

**Effort**: M

---

## P3 — Medium

### 3.1 No 401 Interceptor in Frontend

**Problem**: The Axios instance has a request interceptor for attaching tokens but no response interceptor. If an access token expires mid-session (edge case where scheduled refresh fails), API calls silently fail or show generic errors.

**Impact**: Poor UX — user sees cryptic errors instead of being redirected to login.

**Solution**:

```typescript
api.interceptors.response.use(
  response => response,
  async error => {
    if (error.response?.status === 401) {
      try {
        await authApi.refresh();
        return api.request(error.config); // retry original request
      } catch {
        clearTokens();
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);
```

**Effort**: S

---

### 3.2 No Monitoring / Health Checks

**Problem**: No Spring Boot Actuator configured on any service. No Prometheus metrics. No structured logging. No alerting.

**Impact**: No visibility into production health. Cannot detect issues before users report them.

**Solution**: Add Actuator + Micrometer to all services. See DEPLOYMENT.md §7 for details.

**Effort**: M

---

### 3.3 CORS Only Allows Localhost

**Problem**: `GatewaySecurityConfig.java` hardcodes `localhost:3000` and `localhost:5173` as allowed origins.

**Impact**: Production frontend on a real domain will be blocked by CORS.

**Solution**: Make CORS origins configurable via environment variable:

```java
@Value("${cors.allowed-origins:http://localhost:5173}")
private String allowedOrigins;

// In corsConfigurationSource():
config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
```

**Effort**: S

---

### 3.4 No Admin Panel

**Problem**: `ROLE_ADMIN` and `ROLE_MODERATOR` enums exist but no admin endpoints, no admin UI, and no way to assign these roles (no endpoint, must be done via direct DB access).

**Impact**: Cannot manage users, view system metrics, or moderate content without database access.

**Solution**:

1. Add `AdminController` with user management endpoints (protected by `ROLE_ADMIN`)
2. Add admin dashboard page to frontend
3. Seed an initial admin user
4. Add role management endpoints

**Effort**: L

---

### 3.5 No Soft Delete

**Problem**: Both user deletion (not implemented) and conversion deletion use hard delete (`deleteById`). No audit trail.

**Impact**: Accidental deletions are unrecoverable. No audit log for compliance.

**Solution**: Add `deletedAt` timestamp field and filter queries to exclude soft-deleted records:

```java
// ConversionProject
private Instant deletedAt; // null = active

// Repository
List<ConversionProject> findByCreatedByAndDeletedAtIsNullOrderByCreatedAtDesc(String email);
```

**Effort**: S

---

### 3.6 Error Handling Inconsistencies

**Problem**:

- Conversion service throws plain `RuntimeException` with no `@RestControllerAdvice` — errors return Spring's default error format (not matching `ErrorResponse` from common)
- User-service `RuntimeException` handler returns 400 for all runtime errors, including "User not found" which should be 404
- No error handling in frontend for `generate` and `delete` operations (silently caught with empty catch blocks)

**Impact**: Inconsistent error responses between services. Silent failures in the frontend.

**Solution**:

1. Add `GlobalExceptionHandler` to conversion-service (copy pattern from user-service)
2. Create specific exception classes (`ResourceNotFoundException`, `DuplicateResourceException`) instead of generic `RuntimeException`
3. Add error toast/notification UI in frontend dashboard

**Effort**: M

---

### 3.7 Inconsistent Prod Database Config

**Problem**: `application-prod.yml` in user-service references MySQL (`com.mysql.cj.jdbc.Driver`) while the dev profile uses PostgreSQL. The parent POM includes an IBM DB2 `jcc` dependency that appears unused.

**Impact**: Production deployment will fail or connect to wrong database type.

**Solution**:

1. Fix `application-prod.yml` to use PostgreSQL driver: `org.postgresql.Driver`
2. Remove IBM DB2 `jcc` dependency from `common/pom.xml` and parent `pom.xml`

**Effort**: S

---

## P4 — Low

### 4.1 No CI/CD Pipeline

**Problem**: No GitHub Actions, GitLab CI, or any other CI/CD configuration.

**Impact**: No automated build verification, no automated deployments, manual quality gate.

**Solution**: Add `.github/workflows/ci.yml`:

```yaml
name: CI
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: mvn clean verify
  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '18' }
      - run: cd frontend && npm ci && npm run build
```

**Effort**: S (basic), M (with tests, Docker build, deployment)

---

### 4.2 No Pagination on List Endpoints

**Problem**: `GET /conversion/conversions` returns all projects for a user with no pagination. As users accumulate projects, response size grows unbounded.

**Impact**: Slow API responses, high memory usage, poor UX for power users.

**Solution**: Add `Pageable` support to the repository and controller:

```java
@GetMapping
public ResponseEntity<Page<ConversionResponse>> list(
        @RequestHeader("X-User-Email") String userEmail,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(conversionService.listByUser(userEmail, PageRequest.of(page, size)));
}
```

**Effort**: S

---

### 4.3 No Icon File Upload

**Problem**: The `iconFile` field accepts a filename string but there is no file upload mechanism. Users cannot actually provide a custom icon for their desktop app.

**Impact**: All generated apps use the default `icon.ico` placeholder. Custom branding is a key selling point.

**Solution**:

1. Add multipart file upload endpoint
2. Store icons in S3/GCS or local filesystem
3. Validate file type (`.ico`, `.png`, `.icns`) and size
4. Reference stored icon path in the generated Electron project

**Effort**: M

---

### 4.4 Refresh Token Not Rotated

**Problem**: The `POST /auth/refresh` endpoint issues a new access token but reuses the same refresh token. If a refresh token is leaked, it can be used for the entire 30-day lifetime.

**Impact**: Extended exposure window if refresh token is compromised.

**Solution**: Issue a new refresh token on each refresh and blacklist the old one:

```java
// In refreshToken():
String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, REFRESH_TOKEN_EXPIRY);
// Blacklist old refresh token
redisTemplate.opsForValue().set("blacklist:" + request.refreshToken(), email, remainingTTL, TimeUnit.MILLISECONDS);
return new RefreshResponse(newAccessToken, newRefreshToken, "Bearer", ACCESS_TOKEN_EXPIRY / 1000);
```

**Effort**: S

---

### 4.5 Generated JS Files Lack Escaping

**Problem**: `ConversionService.generateConfigJs()` uses `String.formatted()` to embed user-provided values (projectName, appTitle, websiteUrl) directly into JavaScript code. If any value contains a single quote, the generated JS will have syntax errors or enable code injection.

**Impact**: Broken generated files, potential code injection in the Electron app.

**Solution**: Use JSON serialization to safely encode values:

```java
private String generateConfigJs(ConversionProject project) {
    ObjectMapper mapper = new ObjectMapper();
    return """
        module.exports = {
          projectName: %s,
          currentVersion: %s,
          appTitle: %s,
          websiteUrl: %s,
          iconFile: %s
        };
        """.formatted(
            mapper.writeValueAsString(project.getProjectName()),
            mapper.writeValueAsString(project.getCurrentVersion()),
            mapper.writeValueAsString(project.getAppTitle()),
            mapper.writeValueAsString(project.getWebsiteUrl()),
            mapper.writeValueAsString(project.getIconFile())
    );
}
```

**Effort**: S

---

### 4.6 Frontend State Management Limitations

**Problem**: Auth state uses React Context only. There is no global state for conversion projects — the dashboard fetches fresh data on mount and after each action. No optimistic updates.

**Impact**: Acceptable for current scale but will cause unnecessary re-renders and network requests as the app grows.

**Solution**: Consider adding Zustand or TanStack Query for:

- Cached conversion project list with stale-while-revalidate
- Optimistic updates on create/delete
- Better loading/error states

**Effort**: M

---

### 4.7 No Logging Standardization

**Problem**: Logging uses Lombok `@Slf4j` with ad-hoc message formats. No structured logging (JSON), no correlation IDs across services, no log levels configured per environment.

**Impact**: Difficult to trace requests across microservices. Log parsing in production is manual.

**Solution**:

1. Add `logback-spring.xml` with JSON encoder for production profile
2. Add correlation ID filter (generate UUID per request, propagate via headers)
3. Configure per-environment log levels

**Effort**: M

---

## Summary Table

| ID | Priority | Title | Effort |
| --- | --- | --- | --- |
| 1.1 | P1 | Hardcoded Secrets | S |
| 1.2 | P1 | No Authorization on Conversions (IDOR) | S |
| 1.3 | P1 | No URL Input Sanitization | S |
| 1.4 | P1 | No HTTPS / Insecure Token Storage | M |
| 2.1 | P2 | No Payment / Subscription System | XL |
| 2.2 | P2 | No Server-Side Build Pipeline | XL |
| 2.3 | P2 | No Tests | L |
| 2.4 | P2 | No Database Migrations | S |
| 2.5 | P2 | Missing Dockerfiles | S |
| 2.6 | P2 | No Rate Limiting | S |
| 2.7 | P2 | Email Verification Not Implemented | M |
| 2.8 | P2 | Password Reset Not Implemented | M |
| 3.1 | P3 | No 401 Interceptor in Frontend | S |
| 3.2 | P3 | No Monitoring / Health Checks | M |
| 3.3 | P3 | CORS Only Allows Localhost | S |
| 3.4 | P3 | No Admin Panel | L |
| 3.5 | P3 | No Soft Delete | S |
| 3.6 | P3 | Error Handling Inconsistencies | M |
| 3.7 | P3 | Inconsistent Prod Database Config | S |
| 4.1 | P4 | No CI/CD Pipeline | S–M |
| 4.2 | P4 | No Pagination | S |
| 4.3 | P4 | No Icon File Upload | M |
| 4.4 | P4 | Refresh Token Not Rotated | S |
| 4.5 | P4 | Generated JS Lacks Escaping | S |
| 4.6 | P4 | Frontend State Management | M |
| 4.7 | P4 | No Logging Standardization | M |

### Recommended Execution Order

1. **Immediate** (day 1): 1.1 (secrets), 1.2 (IDOR), 1.3 (URL sanitization), 3.7 (prod config fix)
2. **This week**: 2.4 (migrations), 2.5 (Dockerfiles), 2.6 (rate limiting), 3.1 (401 interceptor), 3.3 (CORS)
3. **Next sprint**: 2.3 (tests), 2.7 (email verification), 2.8 (password reset), 3.2 (monitoring)
4. **Upcoming**: 2.1 (payments), 2.2 (build pipeline), 3.4 (admin panel)
5. **Backlog**: All P4 items
