# WebToDesk — Deployment Guide

Production deployment guide for the WebToDesk platform.

---

## Table of Contents

1. [Production Environment Requirements](#1-production-environment-requirements)
2. [Docker Deployment](#2-docker-deployment)
3. [Kubernetes Deployment](#3-kubernetes-deployment)
4. [Environment Variable Injection](#4-environment-variable-injection)
5. [SSL / Domain Configuration](#5-ssl--domain-configuration)
6. [Database Migration](#6-database-migration)
7. [Monitoring & Alerting](#7-monitoring--alerting)
8. [Rollback Procedure](#8-rollback-procedure)

---

## 1. Production Environment Requirements

### Minimum Server Specs

| Component | CPU | RAM | Storage | Notes |
| --- | --- | --- | --- | --- |
| Discovery Service | 1 vCPU | 512 MB | 1 GB | Lightweight Eureka server |
| API Gateway | 2 vCPU | 1 GB | 1 GB | Handles all inbound traffic (WebFlux) |
| User Service | 2 vCPU | 1 GB | 2 GB | JPA + Redis connections |
| Conversion Service | 2 vCPU | 1 GB | 5 GB | File generation can be memory-intensive |
| Frontend (static) | — | — | 100 MB | Static files served by CDN/nginx |

### External Services Required

| Service | Purpose | Recommended Provider |
| --- | --- | --- |
| PostgreSQL 15+ | User data | Neon, Supabase, AWS RDS, or self-hosted |
| MongoDB 6+ | Conversion projects | MongoDB Atlas, AWS DocumentDB, or self-hosted |
| Redis 7+ | Token blacklist | Upstash, AWS ElastiCache, or self-hosted |
| Domain + DNS | Custom domain | Cloudflare, Route53 |
| SSL Certificate | HTTPS | Let's Encrypt (free), Cloudflare (free) |
| Container Registry | Docker images | Docker Hub, GitHub Container Registry, AWS ECR |

### Software Prerequisites

- Docker 24+ and Docker Compose v2
- Java 17 JDK (for building from source)
- Node.js 18+ (for frontend build)
- Maven 3.9+ (or use included `mvnw`)

---

## 2. Docker Deployment

### Current State

The project has a `docker-compose.yml` with 4 services. However:

- ⚠️ **Missing Dockerfiles**: `discovery-service` and `conversion-service` do not have Dockerfiles
- ⚠️ **No database containers**: PostgreSQL, MongoDB, and Redis are not in the compose file
- ⚠️ **No frontend container**: The React app is not containerized
- ⚠️ **Hardcoded secrets**: JWT secrets and database credentials are in YAML files

### Recommended Production docker-compose.yml

Below is a complete production-ready Docker Compose file (to replace the current one):

```yaml
version: '3.8'

services:
  # ─── Infrastructure ────────────────────────────
  postgres:
    image: postgres:15-alpine
    container_name: webtodesk-postgres
    environment:
      POSTGRES_DB: webtodesk
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - webtodesk-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb:
    image: mongo:7
    container_name: webtodesk-mongodb
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGO_USER}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD}
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    networks:
      - webtodesk-net
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.runCommand('ping').ok"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: webtodesk-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - webtodesk-net
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ─── Application Services ──────────────────────
  discovery-service:
    build:
      context: .
      dockerfile: discovery-service/Dockerfile   # ⚠️ MUST BE CREATED
    container_name: discovery-service
    ports:
      - "8761:8761"
    networks:
      - webtodesk-net
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 10

  api-gateway:
    build:
      context: .
      dockerfile: api-gateway/Dockerfile
    container_name: api-gateway
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://discovery-service:8761/eureka/
      JWT_ACCESS_SECRET: ${JWT_ACCESS_SECRET}
      JWT_REFRESH_SECRET: ${JWT_REFRESH_SECRET}
    depends_on:
      discovery-service:
        condition: service_healthy
    networks:
      - webtodesk-net

  user-service:
    build:
      context: .
      dockerfile: user-service/Dockerfile
    container_name: user-service
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://discovery-service:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/webtodesk
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_URL: redis://redis:6379
      JWT_ACCESS_SECRET: ${JWT_ACCESS_SECRET}
      JWT_REFRESH_SECRET: ${JWT_REFRESH_SECRET}
    depends_on:
      discovery-service:
        condition: service_healthy
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - webtodesk-net

  conversion-service:
    build:
      context: .
      dockerfile: conversion-service/Dockerfile   # ⚠️ MUST BE CREATED
    container_name: conversion-service
    ports:
      - "8082:8082"
    environment:
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://discovery-service:8761/eureka/
      SPRING_DATA_MONGODB_URI: mongodb://${MONGO_USER}:${MONGO_PASSWORD}@mongodb:27017/webtodesk_conversions?authSource=admin
    depends_on:
      discovery-service:
        condition: service_healthy
      mongodb:
        condition: service_healthy
    networks:
      - webtodesk-net

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile                       # ⚠️ MUST BE CREATED
    container_name: webtodesk-frontend
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - api-gateway
    networks:
      - webtodesk-net

volumes:
  postgres-data:
  mongo-data:
  redis-data:

networks:
  webtodesk-net:
    driver: bridge
```

### Missing Dockerfiles to Create

**discovery-service/Dockerfile**:

```dockerfile
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY common/ common/
COPY discovery-service/ discovery-service/
COPY user-service/pom.xml user-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY conversion-service/pom.xml conversion-service/pom.xml
RUN mvn clean install -pl common -DskipTests
RUN mvn clean package -pl discovery-service -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/discovery-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**conversion-service/Dockerfile**:

```dockerfile
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY common/ common/
COPY conversion-service/ conversion-service/
COPY user-service/pom.xml user-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY discovery-service/pom.xml discovery-service/pom.xml
RUN mvn clean install -pl common -DskipTests
RUN mvn clean package -pl conversion-service -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/conversion-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**frontend/Dockerfile**:

```dockerfile
FROM node:18-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### Build & Deploy Steps

```bash
# 1. Create .env file with all secrets
cp .env.example .env
# Edit .env with production values

# 2. Build and start all services
docker-compose --env-file .env up --build -d

# 3. Verify all services are healthy
docker-compose ps
docker-compose logs -f

# 4. Check Eureka dashboard
curl http://localhost:8761
```

---

## 3. Kubernetes Deployment

> ⚠️ **No Kubernetes manifests currently exist.** Below is the recommended structure.

### Recommended K8s Structure

```
k8s/
├── namespace.yaml                  # webtodesk namespace
├── secrets.yaml                    # JWT secrets, DB credentials (use sealed-secrets or external-secrets)
├── configmaps.yaml                 # Non-secret configuration
├── discovery-service/
│   ├── deployment.yaml
│   └── service.yaml
├── api-gateway/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml                # External ingress with TLS
├── user-service/
│   ├── deployment.yaml
│   └── service.yaml
├── conversion-service/
│   ├── deployment.yaml
│   └── service.yaml
├── frontend/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
└── databases/
    ├── postgres-statefulset.yaml   # Or use managed DB
    ├── mongodb-statefulset.yaml    # Or use MongoDB Atlas
    └── redis-deployment.yaml       # Or use managed Redis
```

### Key Considerations

- Use **Secrets** (preferably sealed-secrets or external-secrets-operator) for credentials
- Use **ConfigMaps** for application.yml overrides
- Set **resource limits** and **requests** on all pods
- Configure **liveness** and **readiness probes** on `/actuator/health`
- Use **Horizontal Pod Autoscaler** (HPA) on api-gateway and conversion-service
- Deploy databases as **managed services** (RDS, Atlas, ElastiCache) in production rather than in-cluster

---

## 4. Environment Variable Injection

### Required .env File

Create `.env` at the project root (never commit this file):

```env
# ─── JWT Secrets (min 32 characters each) ───────
JWT_ACCESS_SECRET=your-access-secret-min-32-chars-here
JWT_REFRESH_SECRET=your-refresh-secret-min-32-chars-here

# ─── PostgreSQL ─────────────────────────────────
POSTGRES_USER=webtodesk
POSTGRES_PASSWORD=strong-random-password-here

# ─── MongoDB ────────────────────────────────────
MONGO_USER=webtodesk
MONGO_PASSWORD=strong-random-password-here

# ─── Spring Profiles ───────────────────────────
SPRING_PROFILES_ACTIVE=prod
```

### Spring Boot Environment Variable Binding

Spring Boot automatically maps environment variables to YAML properties:

| Environment Variable | YAML Property | Example |
| --- | --- | --- |
| `JWT_ACCESS_SECRET` | `jwt.access-secret` | (override via `application-prod.yml` or relaxed binding) |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://host:5432/db` |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `webtodesk` |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `****` |
| `SPRING_DATA_REDIS_URL` | `spring.data.redis.url` | `redis://host:6379` |
| `SPRING_DATA_MONGODB_URI` | `spring.data.mongodb.uri` | `mongodb://user:pass@host/db` |

> ⚠️ **Current Issue**: The `jwt.access-secret` and `jwt.refresh-secret` properties use `@Value` injection with those exact property names. To override via environment variables, you need `JWT_ACCESS_SECRET` → Spring relaxed binding maps to `jwt.access-secret`. Verify this works or add explicit `${JWT_ACCESS_SECRET:default}` placeholders in the YAML files.

### Production application-prod.yml Template

The current `application-prod.yml` in user-service is incomplete. Recommended:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate    # NEVER use 'update' in production
    show-sql: false
  data:
    redis:
      url: ${SPRING_DATA_REDIS_URL}

jwt:
  access-secret: ${JWT_ACCESS_SECRET}
  refresh-secret: ${JWT_REFRESH_SECRET}
```

---

## 5. SSL / Domain Configuration

### Option A: Cloudflare (Recommended for simplicity)

1. Point domain DNS to Cloudflare
2. Add A record pointing to your server IP
3. Enable "Full (strict)" SSL mode in Cloudflare
4. Cloudflare handles SSL termination → your server receives plain HTTP

### Option B: Let's Encrypt + nginx

Create `nginx.conf` for the frontend container:

```nginx
server {
    listen 80;
    server_name yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    # Frontend static files
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # API proxy
    location /user/ {
        proxy_pass http://api-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /conversion/ {
        proxy_pass http://api-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### CORS Update Required

The current CORS config in `GatewaySecurityConfig.java` only allows `localhost` origins:

```java
config.setAllowedOrigins(List.of(
    "http://localhost:3000",
    "http://localhost:5173"
));
```

For production, update to include your domain:

```java
config.setAllowedOrigins(List.of(
    "https://yourdomain.com",
    "https://www.yourdomain.com",
    "http://localhost:5173"  // keep for local dev
));
```

Or better, make it configurable via environment variable.

---

## 6. Database Migration

### Current State

- **PostgreSQL**: Uses `hibernate.ddl-auto: update` in dev — Hibernate auto-creates/modifies tables
- **MongoDB**: Schema-less — collections auto-created on first insert
- **No Flyway or Liquibase** migration scripts exist

### ⚠️ Critical Production Requirement

**Never use `ddl-auto: update` in production.** The `application-prod.yml` correctly uses `ddl-auto: validate`, but no migration scripts exist to create the schema.

### Recommended: Add Flyway Migrations

1. Add Flyway dependency to `user-service/pom.xml`:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

2. Create initial migration at `user-service/src/main/resources/db/migration/V1__init_schema.sql`:

```sql
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE user_profiles (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255),
    phone_number BIGINT,
    avatar_url VARCHAR(255)
);

CREATE TABLE user_roles (
    user_id VARCHAR(255) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(255) NOT NULL
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
```

3. Set `ddl-auto: validate` (already done in prod profile) — Flyway handles schema creation

### MongoDB Indexes

Create a startup migration or index annotation for the `conversions` collection:

```javascript
db.conversions.createIndex({ createdBy: 1, createdAt: -1 });
```

---

## 7. Monitoring & Alerting

### Current State

> ⚠️ **No monitoring is configured.** The codebase has `DEBUG` logging for security and gateway, plus a `RequestLoggingFilter` in user-service, but no structured health checks, metrics, or alerting.

### Recommended Stack

| Tool | Purpose |
| --- | --- |
| **Spring Boot Actuator** | Health checks, metrics, info endpoints |
| **Prometheus** | Metrics collection and storage |
| **Grafana** | Metrics visualization and dashboards |
| **Loki** | Log aggregation |
| **AlertManager** | Alert routing (PagerDuty, Slack, email) |

### Step 1: Add Actuator to each service

Add to each service's `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Add to each `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  endpoint:
    health:
      show-details: when-authorized
  metrics:
    export:
      prometheus:
        enabled: true
```

### Step 2: Key Metrics to Monitor

| Metric | Alert Threshold | Description |
| --- | --- | --- |
| `http_server_requests_seconds` | p99 > 2s | API response time |
| `jvm_memory_used_bytes` | > 80% of max | Memory pressure |
| `hikaricp_connections_active` | > 80% of pool | DB connection exhaustion |
| `conversion_projects_created_total` | — | Business metric: conversions created |
| `auth_login_failures_total` | > 50/min | Brute force detection |
| `redis_connected_clients` | — | Redis connection health |

### Step 3: Health Check Endpoints

Configure each service to expose `/actuator/health`:

- **discovery-service**: `http://localhost:8761/actuator/health`
- **api-gateway**: `http://localhost:8080/actuator/health`
- **user-service**: `http://localhost:8081/actuator/health`
- **conversion-service**: `http://localhost:8082/actuator/health`

---

## 8. Rollback Procedure

### Docker Compose Rollback

```bash
# 1. Stop the failing service
docker-compose stop user-service

# 2. Rebuild with the previous image tag
docker-compose up -d --no-deps --build user-service

# Or if using tagged images:
# Edit docker-compose.yml to use previous image tag
# docker-compose up -d --no-deps user-service
```

### Rolling Back Database Changes

- **PostgreSQL (with Flyway)**: Flyway does not support automatic rollback. Create a compensating migration (e.g., `V3__undo_v2_changes.sql`).
- **MongoDB**: No schema migrations to roll back. If data changes need reverting, restore from backup.
- **Redis**: Token blacklist entries auto-expire. No rollback needed.

### General Rollback Steps

1. **Identify the issue**: Check logs (`docker-compose logs -f <service>`)
2. **Stop the affected service**: `docker-compose stop <service>`
3. **Revert to previous version**: Restore the previous Docker image or code version
4. **Restart**: `docker-compose up -d --no-deps <service>`
5. **Verify**: Check health endpoints and Eureka dashboard
6. **Post-mortem**: Document what went wrong and prevent recurrence

### Backup Strategy (Recommended)

| Store | Backup Frequency | Retention | Method |
| --- | --- | --- | --- |
| PostgreSQL | Daily (automated) | 30 days | `pg_dump` or managed service snapshots |
| MongoDB | Daily (automated) | 30 days | `mongodump` or Atlas scheduled backups |
| Redis | Not critical | — | Token blacklist is ephemeral; no backup needed |
