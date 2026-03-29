# App-Level Documentation & Upskilling Update Brief

Generated: 2026-03-30 00:04:37
Scope: update project documentation from latest code/runtime changes.

## Required output files
- `README.md`
- `CHANGELOG.md`
- `docs/**` (only impacted docs)
- `skills/**` (upskilling + agent protocol updates)

## Source change inventory

### frontend
- [M] frontend/src/hooks/useAuth.tsx (working)
- [M] frontend/src/pages/LoginPage.tsx (working)
- [M] frontend/src/pages/RegisterPage.tsx (working)
- [M] frontend/src/services/api.ts (working)

### root/other
- [M] .ai/app-doc-update-brief.md (commit)

### runtime/deploy
- [M] Dockerfile (working)

### user-service
- [M] user-service/src/main/java/com/example/user_service/config/FirebaseConfig.java (working)
- [M] user-service/src/main/java/com/example/user_service/service/AuthService.java (working)

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

