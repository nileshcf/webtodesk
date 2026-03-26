#!/usr/bin/env sh
set -eu

PORT="${PORT:-80}"

# Alpine nginx includes files from /etc/nginx/http.d/*.conf
mkdir -p /etc/nginx/http.d

envsubst '${PORT}' < /etc/nginx/templates/default.conf.template > /etc/nginx/http.d/default.conf

wait_for_gateway() {
  echo "Waiting for API gateway readiness on 127.0.0.1:8080..."
  max_attempts=180
  attempt=1
  while [ "$attempt" -le "$max_attempts" ]; do
    if wget -qO- http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
      echo "API gateway is ready."
      return 0
    fi
    if [ $((attempt % 10)) -eq 0 ]; then
      echo "Gateway still not ready (attempt $attempt/$max_attempts)"
    fi
    sleep 2
    attempt=$((attempt + 1))
  done
  echo "Gateway did not become ready in time."
  return 1
}

start_jar() {
  jar_glob="$1"
  port="$2"
  name="$3"
  jvm_opts="$4"

  # shellcheck disable=SC2086
  java $jvm_opts -jar $jar_glob --server.port="$port" &
  echo "Started $name (port $port) PID=$!"
}

echo "Starting services..."

# Start discovery first
start_jar "/app/discovery/*.jar" "8761" "discovery" "-Xms64m -Xmx160m"
sleep 30

# Start user + conversion
start_jar "/app/user/*.jar" "8081" "user" "-Xms96m -Xmx220m"
start_jar "/app/conversion/*.jar" "8082" "conversion" "-Xms96m -Xmx220m"
sleep 3

# Start gateway last
start_jar "/app/gateway/*.jar" "8080" "gateway" "-Xms80m -Xmx180m"

wait_for_gateway

echo "Starting nginx..."
nginx -g "daemon off;"