# stale-build-cleanup.yml

**Path:** `.github/workflows/stale-build-cleanup.yml`

> Runs nightly. Cleans up stale GitHub Actions caches (old Buildx layers), prunes old GHCR image versions keeping only the last 10, and closes stale issues/PRs older than 30 days.

```yaml
name: Stale Build Cleanup

on:
  schedule:
    - cron: '0 3 * * *'   # Every night at 03:00 UTC
  workflow_dispatch:
    inputs:
      keep_versions:
        description: 'Number of GHCR image versions to keep'
        type: string
        default: '10'
      dry_run:
        description: 'Dry run — report only, do not delete'
        type: boolean
        default: false

permissions:
  contents: read
  packages: write
  actions: write
  issues: write
  pull-requests: write

env:
  GHCR_REPO: ghcr.io/thecheesybit/webtodesk
  KEEP_VERSIONS: ${{ inputs.keep_versions || '10' }}

jobs:
  # ── Delete old GitHub Actions caches ───────────────────────────────────────
  clean-actions-caches:
    runs-on: ubuntu-latest
    steps:
      - name: List and delete stale caches
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          echo "## Actions Cache Cleanup" >> "$GITHUB_STEP_SUMMARY"

          # List caches older than 7 days
          STALE=$(gh cache list \
            --repo "${{ github.repository }}" \
            --json id,key,createdAt,sizeInBytes \
            --jq '.[] | select(.createdAt < (now - 7*86400 | todate)) | [.id, .key, .sizeInBytes] | @tsv' \
            2>/dev/null || echo "")

          if [ -z "$STALE" ]; then
            echo "No stale caches found" >> "$GITHUB_STEP_SUMMARY"
            exit 0
          fi

          COUNT=0
          FREED=0
          while IFS=$'\t' read -r id key size; do
            echo "Deleting cache: $key (${size} bytes)"
            if [ "${{ inputs.dry_run }}" != "true" ]; then
              gh cache delete "$id" --repo "${{ github.repository }}" 2>/dev/null || true
              FREED=$((FREED + size))
            fi
            COUNT=$((COUNT + 1))
          done <<< "$STALE"

          echo "Deleted $COUNT stale caches, freed ~$((FREED / 1024 / 1024)) MB" >> "$GITHUB_STEP_SUMMARY"

  # ── Prune old GHCR image versions ─────────────────────────────────────────
  clean-ghcr-versions:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout (for .registry-image-tags.json access)
        uses: actions/checkout@v4

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Prune old GHCR versions
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          KEEP="${{ env.KEEP_VERSIONS }}"
          PKG_NAME="webtodesk"
          OWNER="thecheesybit"

          echo "## GHCR Version Cleanup (keeping $KEEP latest)" >> "$GITHUB_STEP_SUMMARY"

          # Get all package versions sorted by creation date (newest first)
          VERSIONS=$(gh api \
            "/users/$OWNER/packages/container/$PKG_NAME/versions" \
            --jq '.[] | [.id, .metadata.container.tags[0], .created_at] | @tsv' \
            2>/dev/null || echo "")

          if [ -z "$VERSIONS" ]; then
            echo "No versions found or insufficient permissions" >> "$GITHUB_STEP_SUMMARY"
            exit 0
          fi

          TOTAL=$(echo "$VERSIONS" | wc -l)
          echo "Total versions: $TOTAL (keeping newest $KEEP)" >> "$GITHUB_STEP_SUMMARY"

          if [ "$TOTAL" -le "$KEEP" ]; then
            echo "Nothing to delete" >> "$GITHUB_STEP_SUMMARY"
            exit 0
          fi

          # Delete all but the newest KEEP versions
          TO_DELETE=$(echo "$VERSIONS" | tail -n +"$((KEEP + 1))")
          COUNT=0
          while IFS=$'\t' read -r version_id tag created_at; do
            [ -z "$version_id" ] && continue
            echo "  Deleting version $version_id (tag: $tag, created: $created_at)"
            if [ "${{ inputs.dry_run }}" != "true" ]; then
              gh api --method DELETE \
                "/users/$OWNER/packages/container/$PKG_NAME/versions/$version_id" \
                2>/dev/null || true
            fi
            COUNT=$((COUNT + 1))
          done <<< "$TO_DELETE"

          echo "Deleted $COUNT old versions" >> "$GITHUB_STEP_SUMMARY"

  # ── Close stale issues and PRs ─────────────────────────────────────────────
  stale-issues:
    runs-on: ubuntu-latest
    steps:
      - name: Mark and close stale issues/PRs
        uses: actions/stale@v9
        with:
          repo-token: ${{ secrets.GITHUB_TOKEN }}
          stale-issue-message: |
            This issue has been automatically marked as stale because it has not had activity in 30 days.
            It will be closed in 7 days if no further activity occurs.
          stale-pr-message: |
            This pull request has been automatically marked as stale because it has not had activity in 30 days.
            It will be closed in 7 days if no further activity occurs.
          close-issue-message: 'Closing due to inactivity. Please reopen if still relevant.'
          close-pr-message: 'Closing due to inactivity. Please reopen if still relevant.'
          days-before-stale: 30
          days-before-close: 7
          stale-issue-label: stale
          stale-pr-label: stale
          # Never mark these labels as stale
          exempt-issue-labels: 'bug,security,space-down,pinned'
          exempt-pr-labels: 'security,pinned'
```