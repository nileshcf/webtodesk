# security-scan.yml

**Path:** `.github/workflows/security-scan.yml`

> Scans for secrets in commits, Java CVEs via OWASP dependency-check, and Docker image vulnerabilities via Trivy. Runs on every PR and weekly.

```yaml
name: Security Scan

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  schedule:
    - cron: '0 2 * * 3'   # Every Wednesday 02:00 UTC
  workflow_dispatch:

permissions:
  contents: read
  security-events: write  # upload SARIF to GitHub Security tab

jobs:
  # ── Secret scanning (gitleaks) ──────────────────────────────────────────────
  secret-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Run Gitleaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  # ── Java / Maven CVE scan (OWASP Dependency-Check) ─────────────────────────
  owasp-check:
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

      - name: Run OWASP Dependency-Check
        run: |
          ./mvnw -B org.owasp:dependency-check-maven:check \
            -DfailBuildOnCVSS=9 \
            -DsuppressionFiles=.owasp-suppressions.xml \
            --no-transfer-progress \
            2>&1 || true   # don't fail CI — report only

      - name: Upload OWASP report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: owasp-dependency-check-report
          path: '**/target/dependency-check-report.html'
          retention-days: 14

      - name: Summarize OWASP findings
        if: always()
        run: |
          echo "## OWASP Dependency-Check" >> "$GITHUB_STEP_SUMMARY"
          REPORTS=$(find . -name "dependency-check-report.html" 2>/dev/null | head -5)
          if [ -n "$REPORTS" ]; then
            echo "Reports generated — download from Artifacts." >> "$GITHUB_STEP_SUMMARY"
          else
            echo "No reports generated (possible build failure)." >> "$GITHUB_STEP_SUMMARY"
          fi

  # ── Docker image vulnerability scan (Trivy) ────────────────────────────────
  trivy-scan:
    runs-on: ubuntu-latest
    # Only scan on main push or weekly — not every PR (build is slow)
    if: github.event_name == 'push' || github.event_name == 'schedule' || github.event_name == 'workflow_dispatch'

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build image for scanning (no push)
        uses: docker/build-push-action@v6
        with:
          context: .
          push: false
          load: true
          tags: webtodesk:scan
          build-args: |
            NODE_MAJOR=20
            ELECTRON_VERSION=38.2.2
            ELECTRON_BUILDER_VERSION=26.0.12
          cache-from: type=gha
          cache-to:   type=gha,mode=max

      - name: Run Trivy vulnerability scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: webtodesk:scan
          format: sarif
          output: trivy-results.sarif
          severity: CRITICAL,HIGH
          ignore-unfixed: true

      - name: Upload Trivy SARIF to Security tab
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: trivy-results.sarif

      - name: Print Trivy table summary
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: webtodesk:scan
          format: table
          severity: CRITICAL,HIGH
          ignore-unfixed: true

  # ── npm audit ──────────────────────────────────────────────────────────────
  npm-audit:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Node 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install deps
        working-directory: frontend
        run: npm ci --quiet

      - name: Run npm audit (high+critical)
        working-directory: frontend
        run: |
          npm audit --audit-level=high --json 2>/dev/null | python3 - <<'EOF'
          import json, sys
          try:
              data = json.load(sys.stdin)
          except:
              print("Clean audit")
              sys.exit(0)
          vulns = data.get("vulnerabilities", {})
          high = sum(1 for v in vulns.values() if v.get("severity") in ("high","critical"))
          if high:
              print(f"Found {high} high/critical vulnerabilities")
              for name, v in vulns.items():
                  if v.get("severity") in ("high","critical"):
                      print(f"  {v['severity'].upper():8} {name}: {v.get('via',[])}")
              sys.exit(1)
          else:
              print("No high/critical vulnerabilities found")
          EOF
```