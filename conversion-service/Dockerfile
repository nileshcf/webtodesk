FROM eclipse-temurin:17-jre-alpine

# Install Node.js, npm, Python, and build tools for Electron builds
RUN apk add --no-cache nodejs npm python3 build-base

# Create cache directories
RUN mkdir -p /tmp/electron-cache /tmp/npm-cache

# Set environment variables for Electron builds
ENV CI=true
ENV ELECTRON_CACHE=/tmp/electron-cache
ENV npm_config_cache=/tmp/npm-cache
ENV ADBLOCK=true

WORKDIR /app
COPY target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
