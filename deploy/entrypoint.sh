#!/usr/bin/env sh
set -eu

PORT="${PORT:-7860}"
export PORT
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

# Ubuntu nginx includes files from /etc/nginx/conf.d/*.conf
mkdir -p /etc/nginx/conf.d
rm -f /etc/nginx/sites-enabled/default

envsubst '${PORT}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf

# Common JVM flags for all services:
#   UseContainerSupport  — reads container CPU/memory limits, not host values
#   G1GC                 — lower pause times, better for concurrent microservices
#   urandom              — avoids SecureRandom entropy starvation (can delay startup by seconds)
#   ansi.enabled=never   — clean logs in Docker/Render (no ANSI escape sequences)
JVM_COMMON="-server -XX:+UseContainerSupport -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.output.ansi.enabled=never"

start_jar() {
  jar_glob="$1"
  port="$2"
  name="$3"
  jvm_opts="$4"
  app_opts="$5"

  # shellcheck disable=SC2086
  java $JVM_COMMON $jvm_opts -jar $jar_glob --server.port="$port" $app_opts &
  echo "[startup] $name started on port $port (PID=$!)"
}

# Waits up to 45s for a service health URL to respond, then proceeds regardless.
wait_ready() {
  url="$1"
  name="$2"
  i=0
  while [ $i -lt 45 ]; do
    if curl -sf "$url" > /dev/null 2>&1; then
      echo "[startup] $name is ready"
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  echo "[startup] WARNING: $name not ready after 45s — proceeding anyway"
}

# Raise open-file limit for the JVM processes — electron-builder opens hundreds
# of handles during packaging. The docker-compose ulimits block sets the hard
# ceiling; this applies it to child processes spawned from this shell.
ulimit -n 65536 2>/dev/null || echo "[startup] WARNING: could not raise nofile limit (non-fatal)"

echo "[startup] Starting services..."

# Render single-container mode: disable Eureka and use direct localhost routing.
# conversion-service gets more heap — it orchestrates Electron builds (npm + electron-builder).
start_jar "/app/user/*.jar"       "8081" "user"       "-Xms128m -Xmx384m" "--eureka.client.enabled=false"
start_jar "/app/conversion/*.jar" "8082" "conversion" "-Xms192m -Xmx512m" "--eureka.client.enabled=false"
start_jar "/app/gateway/*.jar"    "8080" "gateway"    "-Xms128m -Xmx384m" "--eureka.client.enabled=false"

# Wait for conversion-service and gateway before accepting external traffic via nginx.
# This prevents nginx from returning 502 on the very first request.
wait_ready "http://localhost:8082/conversions/health" "conversion-service"
wait_ready "http://localhost:8080/actuator/health"    "api-gateway"

echo "[startup] Starting nginx on port ${PORT}..."
nginx -g "daemon off;"