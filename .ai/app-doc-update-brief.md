# App-Level Documentation & Upskilling Update Brief

Generated: 2026-03-29 18:49:28
Scope: update project documentation from latest code/runtime changes.

## Required output files
- `README.md`
- `CHANGELOG.md`
- `docs/**` (only impacted docs)
- `skills/**` (upskilling + agent protocol updates)

## Source change inventory

### conversion-service
- [M] conversion-service/src/main/java/com/example/conversion_service/controller/BuildController.java (working)
- [M] conversion-service/src/main/java/com/example/conversion_service/dto/CreateConversionRequest.java (working)
- [??] conversion-service/src/main/java/com/example/conversion_service/dto/ModuleConfig.java (working)
- [M] conversion-service/src/main/java/com/example/conversion_service/dto/UpdateConversionRequest.java (working)
- [M] conversion-service/src/main/java/com/example/conversion_service/entity/ConversionProject.java (working)
- [M] conversion-service/src/main/java/com/example/conversion_service/service/BuildService.java (working)
- [M] conversion-service/src/main/java/com/example/conversion_service/service/ConversionService.java (working)
- [M] conversion-service/src/main/java/com/example/conversion_service/service/ModuleRegistry.java (working)
- [M] conversion-service/src/main/resources/templates/electron/config.mustache (working)
- [M] conversion-service/src/main/resources/templates/electron/modules/badge.mustache (working)
- [M] conversion-service/src/main/resources/templates/electron/modules/deep-link.mustache (working)
- [??] conversion-service/src/main/resources/templates/electron/modules/domain-lock.mustache (working)
- [??] conversion-service/src/main/resources/templates/electron/modules/expiry.mustache (working)
- [M] conversion-service/src/main/resources/templates/electron/modules/offline.mustache (working)
- [M] conversion-service/src/main/resources/templates/electron/modules/screen-protect.mustache (working)
- [M] conversion-service/src/main/resources/templates/electron/modules/splash-screen.mustache (working)
- [??] conversion-service/src/main/resources/templates/electron/modules/title-bar.mustache (working)
- [??] conversion-service/src/main/resources/templates/electron/modules/watermark.mustache (working)
- [M] conversion-service/src/main/resources/templates/electron/preload.mustache (working)
- [M] conversion-service/src/test/java/com/example/conversion_service/controller/ConversionControllerTest.java (working)
- [M] conversion-service/src/test/java/com/example/conversion_service/service/ConversionServiceTest.java (working)
- [??] conversion-service/src/test/java/com/example/conversion_service/service/DomainLockModuleTest.java (working)
- [??] conversion-service/src/test/java/com/example/conversion_service/service/ExpiryModuleTest.java (working)
- [M] conversion-service/src/test/java/com/example/conversion_service/service/ModuleRegistryTest.java (working)
- [??] conversion-service/src/test/java/com/example/conversion_service/service/TitleBarModuleTest.java (working)
- [??] conversion-service/src/test/java/com/example/conversion_service/service/WatermarkModuleTest.java (working)

### frontend
- [M] frontend/src/components/ProjectWizard.tsx (working)
- [M] frontend/src/pages/DashboardPage.tsx (working)
- [M] frontend/src/types/index.ts (working)

### root/other
- [??] .ai/ (working)
- [??] ai-doc-sync.ps1 (working)
- [M] README.md (commit)
- [A] registry-pull-run.ps1 (commit)
- [A] registry-push.ps1 (commit)
- [M] test-build.ps1 (working)

### skills
- [M] skills/conversion-service.md (commit)
- [M] skills/FEATURES.md (commit)

## Update rules
1. Reflect implemented behavior only; mark roadmap/deferred items clearly.
2. Keep payment/subscription notes explicitly deferred unless implementation changed.
3. Keep Java baseline explicit: `JAVA_HOME=C:\Program Files\Java\jdk-17`.
4. Ensure README, docs, skills, and changelog stay consistent with each other.
5. Preserve existing functionality and avoid unrelated code changes.

## CHANGELOG entry format
- Add a new top entry with date, summary, and grouped bullets:
  - Added
  - Changed
  - Fixed
  - Docs

## Validation checklist
- README feature/status sections match current runtime behavior.
- docs/API_REFERENCE.md endpoints match live controllers/routes.
- docs/DEPLOYMENT.md and Docker docs match compose/scripts.
- skills docs include latest agent-safe automation flows.

