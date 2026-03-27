#!/usr/bin/env sh
set -eu

PORT="${PORT:-7860}"
export PORT
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

mkdir -p /etc/nginx/http.d
envsubst '${PORT}' \
  < /etc/nginx/templates/default.conf.template \
  > /etc/nginx/http.d/default.conf

start_jar() {
  jar_glob="$1"; port="$2"; name="$3"; jvm_opts="$4"; app_opts="$5"
  java $jvm_opts -jar $jar_glob --server.port="$port" $app_opts &
  echo "Started $name on :$port PID=$!"
}

echo "Starting backend services..."
start_jar "/app/user/*.jar"       8081 "user"       "-Xms128m -Xmx512m" "--eureka.client.enabled=false"
start_jar "/app/conversion/*.jar" 8082 "conversion" "-Xms128m -Xmx512m" "--eureka.client.enabled=false"
start_jar "/app/gateway/*.jar"    8080 "gateway"    "-Xms128m -Xmx512m" "--eureka.client.enabled=false"

echo "Waiting 30s for JVMs to boot..."
sleep 30

echo "Starting nginx..."
nginx -g "daemon off;"