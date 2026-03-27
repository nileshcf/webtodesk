# ---------- FRONTEND BUILD ----------
FROM node:20-alpine AS frontend-builder

WORKDIR /frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
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

RUN mvn -B -pl common,user-service,api-gateway,discovery-service,conversion-service -am dependency:go-offline

COPY common/ common/
COPY user-service/ user-service/
COPY api-gateway/ api-gateway/
COPY discovery-service/ discovery-service/
COPY conversion-service/ conversion-service/

RUN mvn -B -DskipTests clean package -pl common,user-service,api-gateway,discovery-service,conversion-service -am


# ---------- FINAL IMAGE ----------
FROM eclipse-temurin:17-jre-alpine

# Install nginx + required tools
RUN apk add --no-cache nginx bash gettext \
    && mkdir -p /run/nginx \
    && mkdir -p /etc/nginx/conf.d

WORKDIR /app

# Copy nginx template
COPY deploy/nginx.conf.template /etc/nginx/templates/default.conf.template

# Copy entrypoint
COPY deploy/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Copy backend jars
COPY --from=backend-builder /build/discovery-service/target/*.jar /app/discovery/
COPY --from=backend-builder /build/user-service/target/*.jar /app/user/
COPY --from=backend-builder /build/conversion-service/target/*.jar /app/conversion/
COPY --from=backend-builder /build/api-gateway/target/*.jar /app/gateway/

# Copy frontend build
COPY --from=frontend-builder /frontend/dist/ /usr/share/nginx/html/

EXPOSE 7860

ENTRYPOINT ["/entrypoint.sh"]
