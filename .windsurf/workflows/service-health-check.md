# service-health-check.yml

**Path:** `.github/workflows/service-health-check.yml`

```yaml
name: Service Health Check

# Mirrors start-all.ps1 — spins up every microservice and checks all ports respond.
# Runs on PRs touching backend or infra code.

on:
  pull_request:
    branches: [main]
    paths:
      - 'api-gateway/**'
      - 'conversion-service/**'
      - 'user-service/**'
      - 'discovery-service/**'
      - 'common/**'
      - 'pom.xml'
      - 'docker-compose.yml'
      - 'Dockerfile'
  workflow_dispatch:

env:
  JAVA_HOME: /usr/lib/jvm/java-17-openjdk-amd64
  READY_TIMEOUT_SEC: 90

jobs:
  service-health:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Set up Node 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Write .env for CI
        run: |
          # Inject secrets so Spring Boot services can resolve cloud credentials.
          # Mirrors the .env loader block in start-all.ps1.
          cat > .env <<EOF
          MONGODB_URI=${{ secrets.MONGODB_URI }}
          DEVELOPMENT_BUILD=true
          EOF

      - name: Build all Maven modules
        run: ./mvnw -B clean package -DskipTests --no-transfer-progress

      # ── Start services in background (mirrors $services array in start-all.ps1) ──

      - name: Start Discovery Service (port 8761)
        run: |
          cd discovery-service
          nohup ../mvnw -B spring-boot:run -DskipTests > /tmp/discovery.log 2>&1 &
          echo $! > /tmp/discovery.pid

      - name: Wait for Discovery Service
        run: |
          .github/scripts/wait-for-port.sh 8761 ${{ env.READY_TIMEOUT_SEC }}

      - name: Start User Service (port 8081)
        run: |
          cd user-service
          nohup ../mvnw -B spring-boot:run -DskipTests > /tmp/user.log 2>&1 &
          echo $! > /tmp/user.pid

      - name: Start Conversion Service (port 8082)
        run: |
          cd conversion-service
          nohup ../mvnw -B spring-boot:run -DskipTests > /tmp/conversion.log 2>&1 &
          echo $! > /tmp/conversion.pid

      - name: Start API Gateway (port 8080)
        run: |
          cd api-gateway
          nohup ../mvnw -B spring-boot:run -DskipTests > /tmp/gateway.log 2>&1 &
          echo $! > /tmp/gateway.pid

      - name: Wait for backend services
        run: |
          for port in 8081 8082 8080; do
            .github/scripts/wait-for-port.sh $port ${{ env.READY_TIMEOUT_SEC }}
          done

      - name: Install frontend deps & start (port 7860)
        run: |
          cd frontend
          npm ci
          nohup npm run dev > /tmp/frontend.log 2>&1 &
          echo $! > /tmp/frontend.pid

      - name: Wait for frontend
        run: .github/scripts/wait-for-port.sh 7860 ${{ env.READY_TIMEOUT_SEC }}

      # ── Health assertions (mirrors $result.success = ($runningCount -ge 4)) ──
      - name: Assert all services healthy
        run: |
          ALL_OK=true
          for spec in "Discovery:8761" "UserService:8081" "ConversionService:8082" "Gateway:8080" "Frontend:7860"; do
            NAME=${spec%%:*}
            PORT=${spec##*:}
            STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/" || echo "000")
            if [ "$STATUS" = "200" ] || [ "$STATUS" = "302" ] || [ "$STATUS" = "401" ]; then
              echo "[OK] $NAME ($PORT) → HTTP $STATUS"
            else
              echo "[FAIL] $NAME ($PORT) → HTTP $STATUS"
              ALL_OK=false
            fi
          done
          $ALL_OK

      - name: Dump logs on failure
        if: failure()
        run: |
          for svc in discovery user conversion gateway frontend; do
            echo "=== $svc ===" && cat /tmp/${svc}.log 2>/dev/null || true
          done
```
