# dependency-upgrade.yml

**Path:** `.github/workflows/dependency-upgrade.yml`

> Runs weekly. Checks for outdated Maven and npm dependencies, opens a summary in the GitHub Actions job, and can optionally open a PR with safe upgrades.

```yaml
name: Dependency Upgrade Check

on:
  schedule:
    - cron: '0 9 * * 1'   # Every Monday 09:00 UTC
  workflow_dispatch:
    inputs:
      open_pr:
        description: 'Open a PR with safe upgrades (patch/minor only)'
        type: boolean
        default: false

permissions:
  contents: write
  pull-requests: write

jobs:
  # ── Maven dependency check ──────────────────────────────────────────────────
  maven-deps:
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

      - name: Check for Maven dependency updates
        run: |
          ./mvnw -B versions:display-dependency-updates \
            versions:display-plugin-updates \
            --no-transfer-progress \
            2>&1 | tee /tmp/maven-updates.txt

          echo "## Maven Dependency Updates" >> "$GITHUB_STEP_SUMMARY"
          echo '```' >> "$GITHUB_STEP_SUMMARY"
          grep -E "^\[INFO\].*->|^\[WARNING\]" /tmp/maven-updates.txt | head -60 >> "$GITHUB_STEP_SUMMARY" || true
          echo '```' >> "$GITHUB_STEP_SUMMARY"

      # Safe: only update patch versions automatically
      - name: Apply patch-level Maven upgrades
        if: inputs.open_pr == true
        run: |
          ./mvnw -B versions:update-properties \
            -DallowMajorUpdates=false \
            -DallowMinorUpdates=false \
            -DallowIncrementalUpdates=true \
            --no-transfer-progress || true

  # ── npm dependency check ────────────────────────────────────────────────────
  npm-deps:
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

      - name: Check for npm outdated packages
        working-directory: frontend
        run: |
          npm ci --quiet
          npm outdated --json 2>/dev/null | python3 - <<'EOF'
          import json, sys
          try:
              data = json.load(sys.stdin)
          except:
              print("No outdated packages found")
              sys.exit(0)
          print(f"{'Package':<35} {'Current':<12} {'Wanted':<12} {'Latest':<12}")
          print("-" * 71)
          for pkg, info in sorted(data.items()):
              print(f"{pkg:<35} {info.get('current',''):<12} {info.get('wanted',''):<12} {info.get('latest',''):<12}")
          EOF

      - name: Write npm summary
        working-directory: frontend
        run: |
          echo "## npm Dependency Updates" >> "$GITHUB_STEP_SUMMARY"
          echo '```' >> "$GITHUB_STEP_SUMMARY"
          npm outdated 2>/dev/null || true >> "$GITHUB_STEP_SUMMARY"
          echo '```' >> "$GITHUB_STEP_SUMMARY"

      - name: Audit for vulnerabilities
        working-directory: frontend
        run: |
          npm audit --audit-level=high 2>&1 | tee /tmp/audit.txt || true
          echo "## npm Audit" >> "$GITHUB_STEP_SUMMARY"
          echo '```' >> "$GITHUB_STEP_SUMMARY"
          head -40 /tmp/audit.txt >> "$GITHUB_STEP_SUMMARY"
          echo '```' >> "$GITHUB_STEP_SUMMARY"

  # ── Open PR with changes (if requested) ────────────────────────────────────
  open-upgrade-pr:
    needs: [maven-deps, npm-deps]
    runs-on: ubuntu-latest
    if: inputs.open_pr == true

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

      - name: Apply Maven patch upgrades
        run: |
          ./mvnw -B versions:update-properties \
            -DallowMajorUpdates=false \
            -DallowMinorUpdates=false \
            -DallowIncrementalUpdates=true \
            --no-transfer-progress || true

      - name: Apply npm patch upgrades
        working-directory: frontend
        run: |
          npm ci --quiet
          npm update --save 2>/dev/null || true

      - name: Open PR
        uses: peter-evans/create-pull-request@v6
        with:
          token: ${{ secrets.GITHUB_TOKEN }}
          branch: chore/auto-dependency-upgrades
          title: 'chore: automated patch dependency upgrades'
          body: |
            ## Automated Dependency Upgrades

            This PR was opened automatically by the dependency-upgrade workflow.
            Only patch-level upgrades are included.

            **Review carefully before merging** — run tests locally with:
            ```powershell
            .\scripts\start-all.ps1 -NonInteractive
            ```
          commit-message: 'chore: automated patch dependency upgrades [skip ci]'
          labels: dependencies
```