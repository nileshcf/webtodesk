# ---------- FRONTEND BUILD ----------
FROM node:20-alpine AS frontend-builder

WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build


# ---------- FINAL IMAGE ----------
# Maven stage removed entirely — jars are pre-built and committed
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache nginx bash gettext \
    && mkdir -p /run/nginx \
    && mkdir -p /etc/nginx/conf.d

WORKDIR /app

COPY deploy/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY deploy/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Pull jars directly from repo (no build stage)
COPY user-service/target/*.jar       /app/user/
COPY conversion-service/target/*.jar /app/conversion/
COPY api-gateway/target/*.jar        /app/gateway/

COPY --from=frontend-builder /frontend/dist/ /usr/share/nginx/html/

EXPOSE 7860
ENTRYPOINT ["/entrypoint.sh"]