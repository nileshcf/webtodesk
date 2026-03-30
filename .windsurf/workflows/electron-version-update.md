# electron-version-update.yml

**Path:** `.github/workflows/electron-version-update.yml`

> Checks for new Electron and electron-builder releases weekly. If a new version is found, opens a PR that bumps the ARG versions in the Dockerfile and the build-arg defaults in all workflow files and PS1 scripts.

```yaml
name: Electron Version Update

on:
  schedule:
    - cron: '0 10 * * 2'   # Every Tuesday 10:00 UTC
  workflow_dispatch:
    inputs:
      electron_version:
        description: 'Pin a specific Electron version (leave blank for latest)'
        type: string
        default: ''
      builder_version:
        description: 'Pin a specific electron-builder version (leave blank for latest)'
        type: string
        default: ''

permissions:
  contents: write
  pull-requests: write

jobs:
  check-electron-versions:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Node 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Resolve latest Electron versions
        id: versions
        run: |
          # Manual override takes priority
          if [ -n "${{ inputs.electron_version }}" ]; then
            LATEST_ELECTRON="${{ inputs.electron_version }}"
          else
            LATEST_ELECTRON=$(npm view electron version 2>/dev/null)
          fi

          if [ -n "${{ inputs.builder_version }}" ]; then
            LATEST_BUILDER="${{ inputs.builder_version }}"
          else
            LATEST_BUILDER=$(npm view electron-builder version 2>/dev/null)
          fi

          echo "Latest Electron: $LATEST_ELECTRON"
          echo "Latest electron-builder: $LATEST_BUILDER"

          echo "electron=$LATEST_ELECTRON" >> "$GITHUB_OUTPUT"
          echo "builder=$LATEST_BUILDER" >> "$GITHUB_OUTPUT"

      - name: Read current versions from Dockerfile
        id: current
        run: |
          CURRENT_ELECTRON=$(grep 'ARG ELECTRON_VERSION=' Dockerfile | head -1 | cut -d= -f2)
          CURRENT_BUILDER=$(grep 'ARG ELECTRON_BUILDER_VERSION=' Dockerfile | head -1 | cut -d= -f2)
          echo "current_electron=$CURRENT_ELECTRON" >> "$GITHUB_OUTPUT"
          echo "current_builder=$CURRENT_BUILDER" >> "$GITHUB_OUTPUT"
          echo "Current Electron: $CURRENT_ELECTRON"
          echo "Current builder: $CURRENT_BUILDER"

      - name: Check if update is needed
        id: needs_update
        run: |
          NEW_E="${{ steps.versions.outputs.electron }}"
          NEW_B="${{ steps.versions.outputs.builder }}"
          CUR_E="${{ steps.current.outputs.current_electron }}"
          CUR_B="${{ steps.current.outputs.current_builder }}"

          if [ "$NEW_E" != "$CUR_E" ] || [ "$NEW_B" != "$CUR_B" ]; then
            echo "needed=true" >> "$GITHUB_OUTPUT"
            echo "Update needed: Electron $CUR_E → $NEW_E, builder $CUR_B → $NEW_B"
          else
            echo "needed=false" >> "$GITHUB_OUTPUT"
            echo "Already on latest versions — no update needed"
          fi

      # ── Apply version bumps to all relevant files ──────────────────────────
      - name: Bump versions in Dockerfile
        if: steps.needs_update.outputs.needed == 'true'
        run: |
          NEW_E="${{ steps.versions.outputs.electron }}"
          NEW_B="${{ steps.versions.outputs.builder }}"

          sed -i "s/ARG ELECTRON_VERSION=.*/ARG ELECTRON_VERSION=${NEW_E}/" Dockerfile
          sed -i "s/ARG ELECTRON_BUILDER_VERSION=.*/ARG ELECTRON_BUILDER_VERSION=${NEW_B}/" Dockerfile
          echo "Dockerfile updated"

      - name: Bump versions in workflow files
        if: steps.needs_update.outputs.needed == 'true'
        run: |
          NEW_E="${{ steps.versions.outputs.electron }}"
          NEW_B="${{ steps.versions.outputs.builder }}"

          for f in .github/workflows/*.yml; do
            sed -i "s/ELECTRON_VERSION: .*/ELECTRON_VERSION: \"${NEW_E}\"/" "$f"
            sed -i "s/ELECTRON_BUILDER_VERSION: .*/ELECTRON_BUILDER_VERSION: \"${NEW_B}\"/" "$f"
            sed -i "s/ELECTRON_VERSION=${{ steps.current.outputs.current_electron }}/ELECTRON_VERSION=${NEW_E}/g" "$f"
            sed -i "s/ELECTRON_BUILDER_VERSION=${{ steps.current.outputs.current_builder }}/ELECTRON_BUILDER_VERSION=${NEW_B}/g" "$f"
          done
          echo "Workflow files updated"

      - name: Bump versions in PowerShell scripts
        if: steps.needs_update.outputs.needed == 'true'
        run: |
          NEW_E="${{ steps.versions.outputs.electron }}"
          NEW_B="${{ steps.versions.outputs.builder }}"

          find scripts/ -name "*.ps1" -exec \
            sed -i "s/ElectronVersion = \"[^\"]*\"/ElectronVersion = \"${NEW_E}\"/g" {} \;
          find scripts/ -name "*.ps1" -exec \
            sed -i "s/ElectronBuilderVersion = \"[^\"]*\"/ElectronBuilderVersion = \"${NEW_B}\"/g" {} \;
          echo "PS1 scripts updated"

      - name: Open PR
        if: steps.needs_update.outputs.needed == 'true'
        uses: peter-evans/create-pull-request@v6
        with:
          token: ${{ secrets.GITHUB_TOKEN }}
          branch: chore/electron-version-bump
          title: 'chore: bump Electron ${{ steps.current.outputs.current_electron }} → ${{ steps.versions.outputs.electron }}'
          body: |
            ## Electron Version Bump

            | Package | Current | New |
            |---------|---------|-----|
            | electron | `${{ steps.current.outputs.current_electron }}` | `${{ steps.versions.outputs.electron }}` |
            | electron-builder | `${{ steps.current.outputs.current_builder }}` | `${{ steps.versions.outputs.builder }}` |

            ### Files changed
            - `Dockerfile` (ARG defaults)
            - `.github/workflows/*.yml` (build-args and env defaults)
            - `scripts/*.ps1` (param defaults)

            ### Before merging
            - [ ] Run a full Docker build locally with the new version
            - [ ] Trigger a test build in the conversion service to verify the .exe still generates
            - [ ] Check the electron-builder [changelog](https://github.com/electron-userland/electron-builder/blob/master/CHANGELOG.md) for breaking changes

            > This PR was opened automatically by the `electron-version-update` workflow.
          commit-message: 'chore: bump Electron ${{ steps.current.outputs.current_electron }} → ${{ steps.versions.outputs.electron }} [skip ci]'
          labels: dependencies,electron
```