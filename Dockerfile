# syntax=docker/dockerfile:1
# BuildKit cache mounts speed up incremental rebuilds without bloating the image.
# Override versions: docker build --build-arg ELECTRON_VERSION=39.0.0 .
ARG NODE_MAJOR=20
ARG ELECTRON_VERSION=38.2.2
ARG ELECTRON_BUILDER_VERSION=26.0.12

# ---------- FRONTEND BUILD ----------
FROM node:${NODE_MAJOR}-alpine AS frontend-builder

WORKDIR /frontend

COPY frontend/package*.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci

COPY frontend/ ./
# vite.config.ts has envDir: '..' — copy root .env so VITE_FIREBASE_* vars are
# available at build time. Only VITE_ prefixed vars are inlined; secrets stay safe.
# This builder stage is discarded — .env never reaches the final image.
COPY .env /
RUN npm run build


# ---------- BACKEND BUILD ----------
FROM maven:3.9.9-eclipse-temurin-17-alpine AS backend-builder

WORKDIR /build

COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY discovery-service/pom.xml discovery-service/pom.xml
COPY conversion-service/pom.xml conversion-service/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl common,user-service,api-gateway,discovery-service,conversion-service -am dependency:go-offline

COPY common/ common/
COPY user-service/ user-service/
COPY api-gateway/ api-gateway/
COPY discovery-service/ discovery-service/
COPY conversion-service/ conversion-service/

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package -pl common,user-service,api-gateway,discovery-service,conversion-service -am


# ---------- FINAL IMAGE ----------
# Pinned to linux/amd64: Wine, appimagetool, and Electron binaries are x86_64 only.
# On ARM hosts (M1/M2, Ampere, Render free tier) without this flag Docker falls back
# to QEMU emulation — 5-10x slower builds and broken Wine cross-compilation.
FROM --platform=linux/amd64 eclipse-temurin:17-jre-jammy

# Re-declare global ARGs inside this stage (Docker multi-stage scoping requirement).
ARG NODE_MAJOR=20
ARG ELECTRON_VERSION=38.2.2
ARG ELECTRON_BUILDER_VERSION=26.0.12

# Layer 1: System tools + Node.js
# apt cache mounts stay on the build host between docker builds — not in the image.
# This layer is invalidated only when NODE_MAJOR or package names change.
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    dpkg --add-architecture i386 \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_${NODE_MAJOR}.x | bash - \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       nginx bash gettext nodejs python3 build-essential libfuse2 \
    && mkdir -p /run/nginx /etc/nginx/conf.d /tmp/electron-cache /tmp/electron-builder-cache /tmp/npm-cache

# Layer 2: Wine — separate layer; ~500 MB, changes far less often than system tools.
# Splitting prevents Wine reinstall when nginx or Node.js version changes (saves 3+ min).
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       wine wine32 wine64 winbind

# Cache dirs and flags for electron-builder inside Docker
ENV CI=true \
    ADBLOCK=true \
    LANG=C.UTF-8 \
    TZ=UTC \
    ELECTRON_CACHE=/tmp/electron-cache \
    ELECTRON_BUILDER_CACHE=/tmp/electron-builder-cache \
    npm_config_cache=/tmp/npm-cache \
    npm_config_prefer_offline=true \
    npm_config_maxsockets=10 \
    ELECTRON_DISABLE_SANDBOX=1 \
    DBUS_SESSION_BUS_ADDRESS=/dev/null \
    WINEPREFIX=/opt/wine-default \
    WINEARCH=win64 \
    WINEDEBUG=-all

# Pre-initialize Wine prefix into the image layer — saves 10-30s wineboot on every Windows cross-build.
# WINEDEBUG=-all suppresses the thousands of registry/loader debug lines Wine emits by default.
RUN WINEPREFIX=/opt/wine-default WINEARCH=win64 WINEDEBUG=-all wineboot --init 2>&1 | tail -3 || true

# Pre-warm Electron binary into the image layer — saves 100-150 MB download on every build.
# ARG versions are embedded in package.json so this layer re-runs on version bumps.
# NO BuildKit cache mount: downloads must land in the committed image layer
# (/tmp/electron-cache, /tmp/npm-cache) so running containers can reuse them.
RUN mkdir -p /tmp/prewarm && \
    printf '{"name":"prewarm","version":"1.0.0","devDependencies":{"electron":"^%s","electron-builder":"^%s"}}' \
      "${ELECTRON_VERSION}" "${ELECTRON_BUILDER_VERSION}" \
      > /tmp/prewarm/package.json && \
    npm --prefix /tmp/prewarm install --no-audit --no-fund 2>&1 | tail -5 || true; \
    rm -rf /tmp/prewarm || true

WORKDIR /app

# Copy nginx template
COPY deploy/nginx.conf.template /etc/nginx/templates/default.conf.template

# Copy entrypoint
COPY deploy/entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh && chmod +x /entrypoint.sh

# Copy backend jars
COPY --from=backend-builder /build/discovery-service/target/*.jar /app/discovery/
COPY --from=backend-builder /build/user-service/target/*.jar /app/user/
COPY --from=backend-builder /build/conversion-service/target/*.jar /app/conversion/
COPY --from=backend-builder /build/api-gateway/target/*.jar /app/gateway/

# Copy frontend build
COPY --from=frontend-builder /frontend/dist/ /usr/share/nginx/html/

EXPOSE 7860

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD curl -sf http://localhost:7860/conversion/conversions/health | grep -q '\bUP\b\|\bhealthy\b\|\bOK\b' || \
      curl -sf http://localhost:8082/conversions/health > /dev/null

ENTRYPOINT ["/entrypoint.sh"]
