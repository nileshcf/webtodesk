#!/usr/bin/env sh
set -eu

PORT="${PORT:-80}"

envsubst '${PORT}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf

start_jar() {
  jar_glob="$1"
  port="$2"
  name="$3"

  # shellcheck disable=SC2086
  java -jar $jar_glob --server.port="$port" &
  echo "Started $name (port $port) PID=$!"
}

echo "Starting services..."

# Start discovery first
start_jar "/app/discovery/*.jar" "8761" "discovery"
sleep 3

# Start user + conversion
start_jar "/app/user/*.jar" "8081" "user"
start_jar "/app/conversion/*.jar" "8082" "conversion"
sleep 3

# Start gateway last
start_jar "/app/gateway/*.jar" "8080" "gateway"

echo "Starting nginx..."
nginx -g "daemon off;"
