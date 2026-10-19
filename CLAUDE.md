# DentaCloud: project rules for Claude Code

Read PRD.md before any task. It is the source of truth for scope, schema, API and security.

## Decisions already made (do not revisit without asking)
- Android app: native, Java 17, MVVM (ViewModel + LiveData + Repository), Room, Retrofit/OkHttp, Hilt, WorkManager, XML + ViewBinding, single-activity Navigation. minSdk 26.
- Backend: Java 21, Spring Boot 3, Spring Modulith, Spring Security (JWT + rotating refresh tokens), Spring Data JPA, Flyway, PostgreSQL 16 with row-level security, Redis, springdoc-openapi.
- Multi-tenant: shared schema, clinic_id on every tenant table, RLS, tenant from JWT only.
- IDs are UUIDv7. Money is integer paise (long). Timestamps are UTC timestamptz.
- Payments, chart records and audit events are append-only.
- The OpenAPI spec (docs/openapi.yaml) is the API contract; the Android Retrofit client is generated from it.

## Rules
- Work one milestone at a time (PRD section 22.3). Do not start the next milestone until acceptance criteria pass.
- Every DB change is a Flyway migration; every Room schema change has a tested migration.
- Never log patient data (names, phones, notes). Never put patient data in SMS or push text.
- Enforce permissions on the server; never rely on the UI.
- No work on the Android main thread. Use ExecutorService, WorkManager or Room's async APIs. (AsyncTask is allowed only in the lab demo screen.)
- Write tests with the feature: JUnit/Testcontainers on backend, JUnit/Room/Espresso on Android.
- Keep PRs small. After each task, run the build and tests and report results.
- Use dummy data only. Never commit secrets; use environment variables and .env.example.
- If a requirement is ambiguous, ask before building.

## Status and remaining work (updated 2026-10-20)
Done: M0–M7 (see README.md for details and test evidence). M8 in progress.

Remaining:
- M8: V6 migration, a GIN trigram index on upper(full_name) (name search is a seq scan on 100k patients; check with EXPLAIN), then rerun infra/load/run.sh and save infra/load/baseline.json.
- M8 (needs a phone over USB; the emulator can't run with < 12 GB free on C:): R8 smoke test of the benchmark APK against the backend, cold start < 2 s (infra/perf/cold-start.sh, not yet written), and the Espresso flows login/booking/billing (orchestrator, offline demo).
- M8: Trivy rescan after the Firebase dependencies, full regression, docs (report, viva notes).
- M6 leftovers: run the demo script on a real phone and tablet; add team names to docs/report.md.
- Ops gaps: copy backups off-host and set up WAL archiving (current RPO is 24 h); upgrade to Spring Boot 4 to clear .trivyignore.
- M9 (not started): signed .aab, Play internal testing, staging and production deploys, monitoring and alerts, beta clinics.
- Crashlytics delivery: add android/app/google-services.json from a Firebase project, then use the Lab test-crash button in a benchmark build.

Local quirks: ports 8080 and 8081 are taken on this machine, so run the API on 8095 with -Xmx1536m (JDK 23 crashed natively under load otherwise). Backend editor errors in VS Code are a stale classpath; fix with "Java: Clean Java Language Server Workspace".

## Commands
- Backend: `./gradlew :backend:test`, `./gradlew :backend:bootRun`
- Android: `./gradlew :android:app:assembleDebug`, `./gradlew :android:app:testDebugUnitTest`
- Local stack: `docker compose -f infra/docker-compose.yml up -d`
- After changing a dependency (runtime classpaths are locked): `./gradlew :backend:dependencies :android:app:dependencies --write-locks`
- Backups: `infra/backup.sh`, `infra/restore-drill.sh` (need `BACKUP_PASSPHRASE`; see docs/runbook-backup.md)
- Load test: `infra/load/run.sh` (API on :8095; `BIG_EMAIL=<owner>` reuses the seeded 100k clinic)
