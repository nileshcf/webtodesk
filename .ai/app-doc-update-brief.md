# App-Level Documentation & Upskilling Update Brief

Generated: 2026-03-29 22:35:12
Scope: update project documentation from latest code/runtime changes.

## Required output files
- `README.md`
- `CHANGELOG.md`
- `docs/**` (only impacted docs)
- `skills/**` (upskilling + agent protocol updates)

## Source change inventory

### common
- [M] common/src/main/java/com/example/common/security/JwtTokenProvider.java (commit)

### frontend
- [M] frontend/package.json (working)
- [M] frontend/package-lock.json (working)
- [??] frontend/src/config/ (working)
- [M] frontend/src/pages/SettingsPage.tsx (commit)
- [M] frontend/src/services/api.ts (commit)
- [M] frontend/tsconfig.tsbuildinfo (working)
- [M] frontend/vite.config.ts (working)

### root/other
- [??] .registry-image-tags.json (working)
- [M] pom.xml (commit)
- [M] registry-pull-run.ps1 (working)
- [M] registry-push.ps1 (working)
- [M] start-all.ps1 (working)

### user-service
- [M] user-service/pom.xml (commit)
- [A] user-service/src/main/java/com/example/user_service/config/FirebaseConfig.java (commit)
- [A] user-service/src/main/java/com/example/user_service/config/R2ClientConfig.java (commit)
- [A] user-service/src/main/java/com/example/user_service/config/R2Properties.java (commit)
- [M] user-service/src/main/java/com/example/user_service/controller/AuthController.java (commit)
- [M] user-service/src/main/java/com/example/user_service/controller/UserController.java (commit)
- [A] user-service/src/main/java/com/example/user_service/dto/GoogleAuthRequest.java (commit)
- [M] user-service/src/main/java/com/example/user_service/entities/User.java (commit)
- [M] user-service/src/main/java/com/example/user_service/entities/UserProfile.java (commit)
- [A] user-service/src/main/java/com/example/user_service/enums/AuthProvider.java (commit)
- [M] user-service/src/main/java/com/example/user_service/service/AuthService.java (commit)
- [A] user-service/src/main/java/com/example/user_service/service/R2StorageService.java (commit)
- [M] user-service/src/main/java/com/example/user_service/service/UserService.java (commit)
- [M] user-service/src/main/resources/application.yml (commit)

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

