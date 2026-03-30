# registry-ops.yml

**Path:** `.github/workflows/registry-ops.yml`

```yaml
name: Registry Operations (Manual)

# Mirrors registry-push.ps1 and registry-pull-run.ps1 for manual/scheduled use.
# Use this to re-tag, re-push an existing build, or list available versions.

on:
  workflow_dispatch:
    inputs:
      operation:
        description: 'Operation to perform'
        type: choice
        options:
          - list-tags
          - retag-and-push
          - build-and-push
        default: list-tags
      version_tag:
        description: 'Explicit version tag (e.g. v5). Leave blank for auto-increment.'
        type: string
        default: ''
      extra_tags:
        description: 'Additional tags to push (comma-separated, e.g. stable,rc1)'
        type: string
        default: ''
      no_cache:
        description: 'Build without cache'
        type: boolean
        default: false
      notes:
        description: 'Release notes'
        type: string
        default: ''

env:
  REGISTRY: ghcr.io
  GITHUB_REPO: thecheesybit/webtodesk
  SOURCE_IMAGE: webtodesk-webtodesk:latest
  NODE_MAJOR: "20"
  ELECTRON_VERSION: "38.2.2"
  ELECTRON_BUILDER_VERSION: "26.0.12"

permissions:
  contents: write
  packages: write

jobs:
  registry-ops:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      # ── List available tags (mirrors -ListAvailableTags in registry-pull-run.ps1) ──
      - name: List available tags
        if: inputs.operation == 'list-tags'
        run: |
          INDEX=".registry-image-tags.json"
          if [ ! -f "$INDEX" ]; then
            echo "No tag index found at $INDEX"
            exit 0
          fi

          python3 - <<'EOF'
          import json
          with open(".registry-image-tags.json") as f:
              data = json.load(f)
          images = [e for e in data.get("images", [])
                    if e.get("repo","").endswith("thecheesybit/webtodesk")]
          images.sort(key=lambda x: x.get("createdAtUtc",""), reverse=True)
          print(f"{'#':<4} {'Tag':<10} {'Created':<22} {'Digest':<22} Image")
          print("-" * 80)
          for i, e in enumerate(images, 1):
              created = e.get("createdAtUtc","")[:16].replace("T"," ")
              digest  = (e.get("digest") or "")[:20]
              print(f"{i:<4} {e.get('versionTag',''):<10} {created:<22} {digest:<22} {e.get('image','')}")
          EOF

      - name: Set up Docker Buildx
        if: inputs.operation != 'list-tags'
        uses: docker/setup-buildx-action@v3

      - name: Log in to GHCR
        if: inputs.operation != 'list-tags'
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # ── Compute version tag (mirrors Get-NextVersionTag in registry-push.ps1) ──
      - name: Compute version tag
        if: inputs.operation != 'list-tags'
        id: version
        run: |
          EXPLICIT="${{ inputs.version_tag }}"
          if [ -n "$EXPLICIT" ]; then
            echo "version_tag=$EXPLICIT" >> "$GITHUB_OUTPUT"
          else
            INDEX=".registry-image-tags.json"
            REPO="${{ env.REGISTRY }}/${{ env.GITHUB_REPO }}"
            if [ -f "$INDEX" ]; then
              MAX=$(python3 - <<EOF
          import json, re
          with open("$INDEX") as f:
              data = json.load(f)
          repo = "$REPO"
          nums = [int(m.group(1)) for e in data.get("images", [])
                  if e.get("repo") == repo
                  for m in [re.fullmatch(r"v(\d+)", e.get("versionTag",""))]
                  if m]
          print(max(nums) if nums else 0)
          EOF
              )
              echo "version_tag=v$((MAX + 1))" >> "$GITHUB_OUTPUT"
            else
              echo "version_tag=v1" >> "$GITHUB_OUTPUT"
            fi
          fi

      # ── Collect all tags to push (mirrors $allTags in registry-push.ps1) ──
      - name: Resolve tag list
        if: inputs.operation != 'list-tags'
        id: tags
        run: |
          VERSION="${{ steps.version.outputs.version_tag }}"
          REPO="${{ env.REGISTRY }}/${{ env.GITHUB_REPO }}"
          EXTRA="${{ inputs.extra_tags }}"

          TAGS="${REPO}:${VERSION}"$'\n'"${REPO}:latest"
          if [ -n "$EXTRA" ]; then
            IFS=',' read -ra PARTS <<< "$EXTRA"
            for t in "${PARTS[@]}"; do
              t=$(echo "$t" | tr -d '[:space:]')
              [ -n "$t" ] && TAGS="${TAGS}"$'\n'"${REPO}:${t}"
            done
          fi
          echo "tags<<EOF" >> "$GITHUB_OUTPUT"
          echo "$TAGS" >> "$GITHUB_OUTPUT"
          echo "EOF" >> "$GITHUB_OUTPUT"

      # ── Build if requested (mirrors -BuildFirst in registry-push.ps1) ──
      - name: Build and push
        if: inputs.operation == 'build-and-push'
        uses: docker/build-push-action@v6
        id: push
        with:
          context: .
          push: true
          no-cache: ${{ inputs.no_cache == true }}
          tags: ${{ steps.tags.outputs.tags }}
          build-args: |
            NODE_MAJOR=${{ env.NODE_MAJOR }}
            ELECTRON_VERSION=${{ env.ELECTRON_VERSION }}
            ELECTRON_BUILDER_VERSION=${{ env.ELECTRON_BUILDER_VERSION }}
          cache-from: type=gha
          cache-to:   type=gha,mode=max

      # ── Retag existing latest without rebuilding ──
      - name: Retag and push
        if: inputs.operation == 'retag-and-push'
        run: |
          VERSION="${{ steps.version.outputs.version_tag }}"
          REPO="${{ env.REGISTRY }}/${{ env.GITHUB_REPO }}"
          EXTRA="${{ inputs.extra_tags }}"

          docker pull "${REPO}:latest"

          for TAG in "$VERSION" $( echo "$EXTRA" | tr ',' ' ' ); do
            TAG=$(echo "$TAG" | tr -d '[:space:]')
            [ -z "$TAG" ] && continue
            docker tag "${REPO}:latest" "${REPO}:${TAG}"
            docker push "${REPO}:${TAG}"
            echo "Pushed ${REPO}:${TAG}"
          done

      # ── Update tag index (mirrors Save-TagHistory in registry-push.ps1) ──
      - name: Update tag index
        if: inputs.operation != 'list-tags'
        run: |
          INDEX=".registry-image-tags.json"
          VERSION="${{ steps.version.outputs.version_tag }}"
          REPO="${{ env.REGISTRY }}/${{ env.GITHUB_REPO }}"
          NOTES="${{ inputs.notes }}"
          NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

          python3 - <<EOF
          import json, os

          index_path = "$INDEX"
          data = {"schemaVersion": 1, "images": []}
          if os.path.exists(index_path):
              with open(index_path) as f:
                  data = json.load(f)

          all_tags = ["$VERSION", "latest"]
          extra = "$EXTRA"
          if extra:
              all_tags += [t.strip() for t in extra.split(",") if t.strip()]

          entry = {
              "repo":         "$REPO",
              "versionTag":   "$VERSION",
              "image":        f"$REPO:$VERSION",
              "sourceImage":  "${{ env.SOURCE_IMAGE }}",
              "pushedTags":   all_tags,
              "digest":       None,
              "createdAtUtc": "$NOW",
              "notes":        "$NOTES" or None,
          }
          data["images"].append(entry)
          with open(index_path, "w") as f:
              json.dump(data, f, indent=2)
          print(f"Tag index updated → {index_path}")
          EOF

          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add "$INDEX"
          git diff --cached --quiet || git commit -m "chore: update tag index to ${{ steps.version.outputs.version_tag }} [skip ci]"
          git push
```
