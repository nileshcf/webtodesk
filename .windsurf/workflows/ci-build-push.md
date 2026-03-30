# ci-build-push.yml

**Path:** `.github/workflows/ci-build-push.yml`

```yaml
name: CI – Build & Push to Registry

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:
    inputs:
      no_cache:
        description: 'Build without Docker layer cache'
        type: boolean
        default: false
      notes:
        description: 'Release notes for this build'
        type: string
        default: ''

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}           # thecheesybit/webtodesk
  CONTAINER_NAME: webtodesk-app
  NODE_MAJOR: "20"
  ELECTRON_VERSION: "38.2.2"
  ELECTRON_BUILDER_VERSION: "26.0.12"

permissions:
  contents: write        # update .registry-image-tags.json
  packages: write        # push to GHCR

jobs:
  # ────────────────────────────────────────────────────────────────────────────
  build-and-push:
    runs-on: ubuntu-latest
    outputs:
      version_tag: ${{ steps.version.outputs.version_tag }}
      image_ref:   ${{ steps.version.outputs.image_ref }}
      digest:      ${{ steps.push.outputs.digest }}

    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0          # full history for version bump logic

      # ── Determine next version tag (mirrors Get-NextVersionTag in registry-push.ps1) ──
      - name: Compute version tag
        id: version
        run: |
          INDEX=".registry-image-tags.json"
          REPO="${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}"

          if [ -f "$INDEX" ]; then
            MAX=$(python3 - <<'EOF'
import json, sys, re
with open(".registry-image-tags.json") as f:
    data = json.load(f)
repo = "${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}"
nums = [int(m.group(1)) for e in data.get("images", [])
        if e.get("repo") == repo
        for m in [re.fullmatch(r"v(\d+)", e.get("versionTag",""))]
        if m]
print(max(nums) if nums else 0)
EOF
            )
            NEXT="v$((MAX + 1))"
          else
            NEXT="v1"
          fi

          echo "version_tag=$NEXT" >> "$GITHUB_OUTPUT"
          echo "image_ref=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:$NEXT" >> "$GITHUB_OUTPUT"
          echo "Resolved version: $NEXT"

      # ── Docker buildx (mirrors DOCKER_BUILDKIT=1 in docker-rebuild.ps1) ──
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # ── Build (mirrors docker compose build --build-arg in docker-rebuild.ps1) ──
      - name: Build and push image
        id: push
        uses: docker/build-push-action@v6
        with:
          context: .
          push: ${{ github.event_name != 'pull_request' }}
          no-cache: ${{ inputs.no_cache == true }}
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ steps.version.outputs.version_tag }}
          build-args: |
            NODE_MAJOR=${{ env.NODE_MAJOR }}
            ELECTRON_VERSION=${{ env.ELECTRON_VERSION }}
            ELECTRON_BUILDER_VERSION=${{ env.ELECTRON_BUILDER_VERSION }}
          cache-from: type=gha
          cache-to:   type=gha,mode=max
          labels: |
            org.opencontainers.image.source=${{ github.server_url }}/${{ github.repository }}
            org.opencontainers.image.revision=${{ github.sha }}

      # ── Update .registry-image-tags.json (mirrors Save-TagHistory in registry-push.ps1) ──
      - name: Update tag index
        if: github.event_name != 'pull_request'
        run: |
          INDEX=".registry-image-tags.json"
          VERSION="${{ steps.version.outputs.version_tag }}"
          REPO="${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}"
          IMAGE="${REPO}:${VERSION}"
          DIGEST="${{ steps.push.outputs.digest }}"
          NOTES="${{ inputs.notes }}"
          NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

          python3 - <<EOF
          import json, os

          index_path = "$INDEX"
          data = {"schemaVersion": 1, "images": []}
          if os.path.exists(index_path):
              with open(index_path) as f:
                  data = json.load(f)

          entry = {
              "repo":         "$REPO",
              "versionTag":   "$VERSION",
              "image":        "$IMAGE",
              "sourceImage":  "webtodesk-webtodesk:latest",
              "pushedTags":   ["$VERSION", "latest"],
              "digest":       "$DIGEST",
              "createdAtUtc": "$NOW",
              "notes":        "$NOTES" or None,
          }
          data["images"].append(entry)

          with open(index_path, "w") as f:
              json.dump(data, f, indent=2)
          print(f"Tag index updated → {index_path}")
          EOF

      - name: Commit tag index
        if: github.event_name != 'pull_request'
        run: |
          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add .registry-image-tags.json
          git diff --cached --quiet || git commit -m "chore: update tag index to ${{ steps.version.outputs.version_tag }} [skip ci]"
          git push

  # ────────────────────────────────────────────────────────────────────────────
  summary:
    needs: build-and-push
    runs-on: ubuntu-latest
    if: github.event_name != 'pull_request'
    steps:
      - name: Print deploy instructions
        run: |
          echo "## Published: ${{ needs.build-and-push.outputs.version_tag }}" >> "$GITHUB_STEP_SUMMARY"
          echo "" >> "$GITHUB_STEP_SUMMARY"
          echo '```powershell' >> "$GITHUB_STEP_SUMMARY"
          echo "# Pull & run on any machine:" >> "$GITHUB_STEP_SUMMARY"
          echo ".\registry-pull-run.ps1 -GitHubRepo ${{ github.repository }} -VersionTag ${{ needs.build-and-push.outputs.version_tag }} -StopExisting" >> "$GITHUB_STEP_SUMMARY"
          echo '```' >> "$GITHUB_STEP_SUMMARY"
```
