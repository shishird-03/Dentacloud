# DentaCloud

A multi-clinic dental practice SaaS: a native **Android app** (Java 17, MVVM, Room, Hilt; phone and tablet) that works fully offline and syncs with a multi-tenant **Spring Boot 3 backend** (Java 21, PostgreSQL 16 with row-level security). It started as the Mobile Application Development lab (CS3604-1) mini-project, where every syllabus experiment is a real feature, and is being extended toward production.

| Document | What it is |
|---|---|
| [PRD.md](PRD.md) | Product requirements: scope, schema, API, security, milestones (source of truth) |
| [CLAUDE.md](CLAUDE.md) | Project rules, fixed decisions, commands, **current status and remaining work** |
| [docs/report.md](docs/report.md) | Mini-project report: architecture, syllabus mapping, testing, security |
| [docs/viva-notes.md](docs/viva-notes.md) | Likely viva questions with short answers |
| [docs/demo-script.md](docs/demo-script.md) | Walkthrough that shows every lab experiment |
| [docs/runbook-backup.md](docs/runbook-backup.md) | Backups, monthly restore drill, incident restore |
| [docs/openapi.yaml](docs/openapi.yaml) | API contract; the Android Retrofit client is generated from it |

## Repository layout

```
android/app/          Android app (MVVM: ui/ → ViewModels → data/repo → Room + Retrofit)
backend/              Spring Boot + Spring Modulith (identity, patient, scheduling, clinical,
                      billing, prescription, notification, sync, audit)
  src/main/resources/db/migration/   Flyway V1–V5
infra/
  docker-compose.yml  PostgreSQL 16 + Redis for local development
  backup.sh, restore.sh, restore-drill.sh, backup-counts.sql   encrypted backups
  load/               k6 load test (k6.js, run.sh, seed-100k.sql)
docs/                 report, viva notes, demo script, runbook, OpenAPI contract
.github/workflows/ci.yml   backend tests, Android tests and build, Trivy dependency scan
```

## Run it locally

**App only (offline demo, no backend):**
```bash
./gradlew :android:app:installDebug      # phone connected with USB debugging on
```
Open the app and tap **Try the offline demo**.

**Full stack:**
```bash
docker compose -f infra/docker-compose.yml up -d
./gradlew :backend:bootJar
java -Xmx1536m -jar backend/build/libs/backend-0.1.0.jar --server.port=8095

# phone over USB
adb reverse tcp:8095 tcp:8095
./gradlew :android:app:installDebug -PapiBaseUrl=http://localhost:8095/
```
In the app, tap **Create a clinic**. API docs are at http://localhost:8095/swagger-ui.html.

Notes for this development machine:
- Ports 8080 and 8081 are used by other local services, so the API runs on 8095.
- The heap cap (`-Xmx1536m`) avoids a native JVM crash seen under load on JDK 23 when memory was tight.
- The Android emulator needs about 12 GB of free disk; use a real phone otherwise.
- Copy `.env.example` to `.env` for real keys. Every value in the repo is a dummy.

## Tests and checks

| Check | Command | Latest result (19–20 Oct 2026) |
|---|---|---|
| Backend integration (Testcontainers, needs Docker) | `./gradlew :backend:test` | **38/38 pass** |
| Android unit (JUnit, Robolectric, Room) | `./gradlew :android:app:testDebugUnitTest` | **35/35 pass** |
| Backup restore drill | `BACKUP_PASSPHRASE=... infra/restore-drill.sh` | **28/28 checks match**; rejects a wrong passphrase, a tampered file and missing data |
| Dependency scan (Trivy, in CI) | see `.github/workflows/ci.yml` | **0 open HIGH/CRITICAL** |
| Load test (k6, 500 users, 100k-patient clinic) | `infra/load/run.sh` | p50 **10 ms**, p95 **103 ms**, p99 **163 ms**; search p95 **127 ms**; 0.01 % failed |
| R8 release build | `./gradlew :android:app:assembleRelease` | builds; APK **3.0 MB** (limit 40 MB) |

## What has been built

### M0: Foundations
Monorepo with Gradle builds for the backend and the Android app, Docker Compose with PostgreSQL 16 and Redis, a GitHub Actions CI workflow, the OpenAPI contract, and an Android app with a splash animation and Hilt.

### M1: Android offline core
Room database for patients and appointments, patient search, a two-step registration form with camera photo, booking with date picker and spinner, the billing calculator and procedure checkboxes with a Toast total, options and context menus, SMS and call intents, confirmation dialogs, and a SharedPreferences session. Everything works with no backend.

### M2: Backend identity and tenancy
- Clinic signup and login.
- 15-minute JWTs and rotating refresh tokens with reuse detection (a reused token revokes its whole family).
- Roles and permission scopes checked on every endpoint.
- PostgreSQL row-level security on every tenant table, with the tenant taken only from the JWT.
- A tenant-isolation test suite.

### M3: Patients, scheduling and sync
- Patient and appointment APIs.
- Two PostgreSQL exclusion constraints (dentist and chair) that make double-booking impossible, proven by a 100-thread booking test.
- `/sync/pull` and `/sync/push`.
- The Retrofit client generated from `docs/openapi.yaml`.
- On Android: a `pending_op` queue, WorkManager sync and a sync status chip.

### M4: Clinical and billing
Visits, an FDI dental chart drawn on a custom Canvas view with history, treatment plans, invoices, an append-only payment ledger with `Idempotency-Key`, and receipt and prescription PDFs generated on the device.

### M5: Reminders, maps and platform topics
- A transactional outbox worker sending SMS and data-only FCM. No patient data appears in message text.
- A foreground reminder service, and a reminder notification that opens the appointment.
- A low-battery banner (broadcast receiver).
- A WebView tips screen, and a clinic map (osmdroid) with a Navigate button.
- The lab-only AsyncTask screen, and a CSV export with a progress bar.

### M6: Mini-project complete (docs)
Demo script, report and viva notes, mapping all 20 lab experiments and 5 course objectives to screens and code.

### M7: Security hardening
- **Per-clinic encryption keys:** AES-256-GCM on phone, address, history, allergies and visit notes. Each clinic's key is wrapped by the master key (`FIELD_ENC_KEY`), and the clinic id is bound into every ciphertext (Flyway V5, `shared/FieldCrypto.java`).
- **Blind index:** HMAC phone index, so encrypted phones stay searchable.
- **Hash-chained audit log:** now also records every clinical read. Owners can list it (`GET /api/v1/audit-events`) and verify the chain (`GET /api/v1/audit-events/verify`).
- **Device:** optional biometric app lock in Settings, `FLAG_SECURE` on patient screens, and tokens in Keystore-backed storage.
- **Encrypted backups:** gpg AES-256, with a restore drill and a runbook.
- **Dependency scan:** dependency locking (`gradle.lockfile`) plus Trivy in CI. Jackson, Tomcat and the PostgreSQL driver were upgraded for CVEs. Two Spring MVC CVEs that aren't reachable here are documented in `.trivyignore`.

### M8: Quality and performance (in progress)
- A `benchmark` build type: the R8-minified release, signed with the debug key for local testing.
- Crashlytics wired in. It switches on only when `android/app/google-services.json` exists, stays off in debug builds, and sends no custom keys or user ids. A test-crash button appears in More → Lab when it's live.
- The k6 load test with a 100,000-patient seed. The baseline passes every PRD target.

## Remaining work

**M8**
- [ ] Flyway V6: a trigram index on `upper(full_name)`, because name search is a sequential scan at 100k patients. Rerun the load test and save `infra/load/baseline.json`.
- [ ] On a real phone (USB): an R8 smoke test against the backend, cold start < 2 s, and Espresso flows for login, booking and billing.
- [ ] Trivy rescan after the Firebase dependencies, full regression run, and report and viva-notes updates.

**M6 leftovers**
- [ ] Run the demo script on a real phone and a tablet.
- [ ] Add team names to `docs/report.md`.

**Operations**
- [ ] Copy backups off the database host and set up WAL archiving. The current RPO is 24 hours; the PRD target is 15 minutes.
- [ ] Upgrade to Spring Boot 4 (Spring Framework 7) to clear `.trivyignore`.
- [ ] Crashlytics delivery: add `google-services.json` from a Firebase project and send a test crash.

**M9: Release (not started)**
- [ ] Signed `.aab` and Play internal testing.
- [ ] Staging and production deploys.
- [ ] Monitoring and alerts.
- [ ] Beta clinics.

## Conventions
Rules, fixed technical decisions and commands live in [CLAUDE.md](CLAUDE.md). In short: one milestone at a time, every database change is a Flyway migration, permissions are enforced on the server, no patient data in logs or notifications, dummy data only, and secrets only in environment variables.
