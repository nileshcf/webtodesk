---
auto_execution_mode: 2
---
# ai-doc-sync.yml

**Path:** `.github/workflows/ai-doc-sync.yml`

```yaml
name: AI Doc Sync

# Mirrors ai-doc-sync.ps1 — generates an update brief from changed files,
# then optionally invokes an AI agent to keep docs/skills/README/CHANGELOG in sync.

on:
  push:
    branches: [main]
    paths:
      # Only trigger when source code actually changes (not doc-only commits)
      - 'api-gateway/**'
      - 'conversion-service/**'
      - 'user-service/**'
      - 'discovery-service/**'
      - 'common/**'
      - 'frontend/**'
      - 'deploy/**'
      - 'docker-compose.yml'
      - 'Dockerfile'
  workflow_dispatch:
    inputs:
      since_ref:
        description: 'Compare changes since this ref (default: HEAD~1)'
        type: string
        default: 'HEAD~1'
      only_working_tree:
        description: 'Use working-tree diff only (no committed range)'
        type: boolean
        default: false

permissions:
  contents: write

jobs:
  ai-doc-sync:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0          # full history needed for git diff range

      - name: Determine diff range
        id: range
        run: |
          SINCE="${{ inputs.since_ref || 'HEAD~1' }}"
          echo "since_ref=$SINCE" >> "$GITHUB_OUTPUT"

      # ── Collect changed files (mirrors Parse-NameStatusLine / Parse-PorcelainLine) ──
      - name: Collect changed files
        id: changes
        run: |
          SINCE="${{ steps.range.outputs.since_ref }}"

          # Committed changes since ref
          COMMITTED=$(git diff --name-status "${SINCE}..HEAD" 2>/dev/null || true)

          # Working-tree changes (staged + unstaged)
          WORKING=$(git status --porcelain 2>/dev/null || true)

          # Deduplicate and write out
          {
            echo "$COMMITTED"
            echo "$WORKING"
          } | awk 'NF' | sort -u > /tmp/changed_files.txt

          COUNT=$(wc -l < /tmp/changed_files.txt)
          echo "count=$COUNT" >> "$GITHUB_OUTPUT"
          echo "Changed files: $COUNT"
          cat /tmp/changed_files.txt

      # ── Generate update brief (mirrors the $sb StringBuilder block in ai-doc-sync.ps1) ──
      - name: Generate doc-update brief
        if: steps.changes.outputs.count > 0
        run: |
          mkdir -p .ai

          SINCE="${{ steps.range.outputs.since_ref }}"
          NOW=$(date '+%Y-%m-%d %H:%M:%S')

          # Area classifier (mirrors Get-Area in ai-doc-sync.ps1)
          classify() {
            case "$1" in
              docs/*)              echo "docs" ;;
              skills/*)            echo "skills" ;;
              frontend/*)          echo "frontend" ;;
              conversion-service/*)echo "conversion-service" ;;
              user-service/*)      echo "user-service" ;;
              api-gateway/*)       echo "api-gateway" ;;
              common/*)            echo "common" ;;
              deploy/*|*docker*|*.yml|*.yaml) echo "runtime/deploy" ;;
              *)                   echo "root/other" ;;
            esac
          }

          BRIEF_FILE=".ai/app-doc-update-brief.md"

          {
            echo "# App-Level Documentation & Upskilling Update Brief"
            echo ""
            echo "Generated: $NOW"
            echo "Scope: update project documentation from latest code/runtime changes."
            echo ""
            echo "## Required output files"
            echo '- `README.md`'
            echo '- `CHANGELOG.md`'
            echo '- `docs/**` (only impacted docs)'
            echo '- `skills/**` (upskilling + agent protocol updates)'
            echo ""
            echo "## Source change inventory"
            echo ""

            # Group by area
            declare -A AREA_LINES
            while IFS= read -r line; do
              [ -z "$line" ] && continue
              STATUS=$(echo "$line" | awk '{print $1}')
              PATH_=$(echo "$line" | awk '{print $NF}')
              AREA=$(classify "$PATH_")
              AREA_LINES["$AREA"]+="- [$STATUS] $PATH_"$'\n'
            done < /tmp/changed_files.txt

            for area in $(echo "${!AREA_LINES[@]}" | tr ' ' '\n' | sort); do
              echo "### $area"
              echo "${AREA_LINES[$area]}"
            done

            echo "## Update rules"
            echo "1. Reflect implemented behavior only; mark roadmap/deferred items clearly."
            echo "2. Keep payment/subscription notes explicitly deferred unless implementation changed."
            echo "3. Keep Java baseline explicit: \`JAVA_HOME=C:\\Program Files\\Java\\jdk-17\`."
            echo "4. Ensure README, docs, skills, and changelog stay consistent with each other."
            echo "5. Preserve existing functionality and avoid unrelated code changes."
            echo ""
            echo "## CHANGELOG entry format"
            echo "- Add a new top entry with date, summary, and grouped bullets:"
            echo "  - Added"
            echo "  - Changed"
            echo "  - Fixed"
            echo "  - Docs"
            echo ""
            echo "## Validation checklist"
            echo "- README feature/status sections match current runtime behavior."
            echo "- docs/API_REFERENCE.md endpoints match live controllers/routes."
            echo "- docs/DEPLOYMENT.md and Docker docs match compose/scripts."
            echo "- skills docs include latest agent-safe automation flows."
          } > "$BRIEF_FILE"

          echo "Brief written to $BRIEF_FILE"
          cat "$BRIEF_FILE"

      # ── Commit the brief so it's available to agents or PR reviewers ──
      - name: Commit update brief
        if: steps.changes.outputs.count > 0
        run: |
          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add .ai/app-doc-update-brief.md
          git diff --cached --quiet || \
            git commit -m "docs: regenerate AI doc-update brief [skip ci]" && \
            git push

      - name: No changes detected
        if: steps.changes.outputs.count == 0
        run: echo "No source changes detected — skipping brief generation."
```
