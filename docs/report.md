# DentaCloud: mini-project report

**Course:** Mobile Application Development Laboratory (CS3604-1)
**Project:** DentaCloud, a dental clinic management app (native Android + Spring Boot)
**Team:** A (backend and security) · B (Android data and platform) · C (Android UI and design). Fill in names and register numbers.

---

## 1. Abstract

Most small dental clinics in India keep appointments, charts and bills on paper, in Excel or on WhatsApp. DentaCloud is a native Android app (phone and tablet) that a clinic can use for patients, appointments, a dental chart, treatment plans, billing, prescriptions and reminders. It works fully offline on a Room (SQLite) database. When a backend is available, it syncs with a multi-tenant Spring Boot REST API. Every Part A and Part B lab experiment appears as a real feature of the app (Section 5).

## 2. Problem statement and objectives

**Problem.** Clinic staff need one fast tool that works without internet, prevents double-booking, keeps a tamper-proof payment record and reminds patients, without sending patient data anywhere it should not go.

**Objectives (course objectives → where they are met)**

| Course objective | Met by |
|---|---|
| 1. Android architecture | MVVM with Hilt, single-activity Navigation, lifecycle-aware LiveData (Section 3) |
| 2. UI and Android components | 20+ screens, a foreground service, two broadcast receivers, a FileProvider, WorkManager workers |
| 3. Data handling with SQLite | Room database, 14 entities, version 2 with a tested migration |
| 4. Android web service | Retrofit client generated from `docs/openapi.yaml`, JWT with refresh-token rotation |
| 5. Maps and navigation | Clinic location screen (osmdroid map) with a **Navigate** button that opens the system navigation intent |

## 3. Architecture

### 3.1 Overview

```
Android app (Java 17)                         Backend (Java 21)
┌──────────────────────────────┐             ┌──────────────────────────────┐
│ Fragments (XML + ViewBinding)│             │ Spring Boot 3 + Modulith     │
│        │ observe LiveData     │   HTTPS     │ identity · patient ·         │
│ ViewModels (Hilt)            │  JSON/JWT   │ scheduling · clinical ·      │
│        │                      │ ─────────▶  │ billing · prescription ·     │
│ Repositories ── Room (SQLite)│  sync/pull  │ notification · sync · audit  │
│        │                      │  sync/push  │        │                     │
│ SyncEngine + WorkManager ────┼────────────▶ │ PostgreSQL 16 (RLS) · Redis  │
└──────────────────────────────┘             └──────────────────────────────┘
```

### 3.2 Android layers (MVVM)

- **View:** fragments in `ui/*` inside one `MainActivity`, which hosts a Navigation graph (`res/navigation/nav_graph.xml`). Phones get a bottom bar and tablets (`layout-sw600dp`) get a navigation rail with the patient list beside the profile.
- **ViewModel:** holds screen state as `LiveData` and survives rotation. Fragments observe with `getViewLifecycleOwner()`, so observers are removed when the view is destroyed.
- **Repository:** `data/repo/*`. It is the only layer that touches Room and the network. All writes run off the main thread on an `ExecutorService`.
- **Data:** Room entities and DAOs in `data/local`. Every offline write also adds a `pending_op` row so the change can be pushed later.
- **DI:** Hilt modules in `di/` (`AppModule`, `NetworkModule`, `Migrations`).

### 3.3 Lifecycle notes

- Fragments clear their ViewBinding in `onDestroyView` (for example `SplashFragment`), so a fragment on the back stack does not keep its old view alive.
- `BatteryReceiver` is registered in `MainActivity.onStart` and unregistered in `onStop`, so it only runs while the app is visible.
- `ReminderForegroundService` is a started foreground service. It keeps a persistent notification while chair-side reminders are on. After a reboot, `BootReceiver` resumes reminders through WorkManager, because Android blocks most foreground-service starts from boot.
- `SyncWorker` (WorkManager) runs with a network constraint and exponential backoff, so it survives process death.

### 3.4 Backend

Spring Modulith modules, one per business area, communicate through events (`ClinicCreated`, `AppointmentScheduled`). Every tenant table has `clinic_id` and a PostgreSQL row-level security policy. `TenantFilter` takes the clinic from the JWT and sets `app.clinic_id` per transaction. The tenant is never read from the request body.

## 4. Modules

| Module | Android | Backend |
|---|---|---|
| Auth | Login, clinic signup, offline demo login, session in SharedPreferences | Signup, login, 15-min JWT, rotating refresh tokens with reuse detection |
| Patients | List with search, two-step form with photo, profile with call/SMS | CRUD API, `P-0001` style codes |
| Scheduling | Booking with date picker and spinner, day/week calendar | Two PostgreSQL exclusion constraints (dentist, chair), idempotent booking |
| Clinical | FDI tooth chart (custom Canvas view) with history slider, visits, treatment plans | Append-only chart records, visits, plans |
| Billing | Procedure checkboxes, calculator, invoices, split payments, refunds, receipt PDF | Invoices, append-only payment ledger with `Idempotency-Key` |
| Prescriptions | Prescription editor, PDF share | Prescription API |
| Reminders | Foreground service, notification that opens the appointment | Transactional outbox worker sending SMS and data-only FCM (no patient data in text) |
| Sync | `pending_op` queue, `SyncEngine`, WorkManager, sync status chip | `/sync/pull` (cursor based), `/sync/push` (per-op transactions, conflicts reported) |
| Platform | Battery banner, WebView tips, clinic map, AsyncTask lab, CSV export with progress | — |

## 5. Syllabus experiment mapping

All paths are under `android/app/src/main/java/com/dentacloud/app/`. The demo steps refer to `docs/demo-script.md`.

### Part A

| # | Experiment | Feature | Code | Demo step |
|---|---|---|---|---|
| 1 | Checkboxes and Toast total | Tick procedures on a bill and show the total | `ui/billing/BillingFragment.java`, `util/BillMath.java` | 10 |
| 2 | Calculator (+, −, ×, ÷, C) | Billing calculator | `util/Calculator.java`, `ui/billing/BillingFragment.java` | 10 |
| 3 | Random color on click | Patient card and appointment chip colors | `ui/common/Colors.java` | 6, 9 |
| 4 | Options menu | Overflow menu switching Patients and Appointments | `ui/MainActivity.java` | 7 |
| 5 | Context menu with Intent | Long-press patient: change color / edit / delete | `ui/patients/PatientListFragment.java`, `PatientAdapter.java` | 6 |
| 6 | SMS using Intent | Send a reminder from the patient profile | `ui/common/Intents.java` | 4 |
| 7 | Phone call | One-tap call from profile and list | `ui/common/Intents.java` | 4, 5 |
| 8 | Date picker, spinner, Toast | Booking an appointment, patient date of birth | `ui/calendar/BookAppointmentFragment.java`, `ui/patients/PatientFormStep1Fragment.java` | 3, 8 |
| 9 | Confirmation dialog | Confirm logout, delete, refund | `ui/common/Dialogs.java` | 2, 6, 11 |
| 10 | Camera capture as background | Patient photo as the profile header background | `ui/patients/PatientFormStep2Fragment.java`, `ui/common/Photos.java` | 3, 4 |

### Part B

| # | Experiment | Feature | Code | Demo step |
|---|---|---|---|---|
| 1 | Background service | Foreground service watching upcoming appointments | `service/ReminderForegroundService.java`, `service/Reminders.java` | 14 |
| 2 | AsyncTask 1 to 1000 with progress bar | Lab screen (AsyncTask); production export uses ExecutorService | `ui/lab/AsyncTaskLabFragment.java`, `data/repo/ExportRepository.java` | 16 |
| 3 | Battery broadcast receiver | Low-battery banner (≤ 20 %, unplugged) | `receiver/BatteryReceiver.java`, `ui/MainActivity.java` | 15 |
| 4 | Insert and display DB values | Patient registration and list from Room | `data/local/dao/PatientDao.java`, `ui/patients/PatientListFragment.java` | 3, 5 |
| 5 | Search, then call | Search by name, phone or `P-0001`, then call | `data/local/dao/PatientDao.java`, `ui/patients/PatientListFragment.java` | 5 |
| 6 | Two-screen form | Two-step patient registration with photo | `ui/patients/PatientFormStep1Fragment.java`, `PatientFormStep2Fragment.java`, `PatientFormViewModel.java` | 3 |
| 7 | Notification opens another screen | Reminder notification deep-links to the appointment | `service/Reminders.java` (`NavDeepLinkBuilder`), `nav_graph.xml` (`dentacloud://appointment/{id}`) | 14 |
| 8 | WebView, two pages | Dental care tips → About our clinic | `ui/tips/TipsFragment.java`, `assets/tips/index.html`, `assets/tips/clinic.html` | 17 |
| 9 | SharedPreferences save/delete/retrieve | Login session, tokens, UI preferences | `data/local/SessionStore.java`, `TokenStore.java`, `UiPrefs.java` | 2 |
| 10 | Animations (blink, move, rotate, zoom) | Splash animation, tooth blink, chart zoom | `res/anim/*.xml`, `ui/splash/SplashFragment.java`, `ui/chart/ToothChartView.java` | 1, 12 |

**Note on "two activities".** The app follows Google's single-activity guidance, so where the syllabus says "two activities", the app uses two navigation destinations (fragments) with arguments passed in a `Bundle` (`ui/common/Args.java`). The concepts are the same: an explicit navigation, data passed between screens, and the back stack.

## 6. Database design

**On device (Room, version 2).** The tables are patient, appointment, procedure, visit, tooth_record, treatment_plan, plan_item, invoice, invoice_line, payment_entry, prescription, prescription_item, pending_op and sync_state. Schemas are exported to `android/app/schemas/`, and `MigrationTest` checks the 1 → 2 migration.

**Server (PostgreSQL 16, Flyway V1 to V5).**
- V1: identity and tenancy (clinic, user, roles, members, refresh tokens) with RLS.
- V2: patients and appointments, including:
  ```sql
  CONSTRAINT no_dentist_overlap EXCLUDE USING gist (dentist_id WITH =, tstzrange(start_at, end_at) WITH &&)
  ```
- V3: visits, chart, plans, invoices and the payment ledger.
- V4: the outbox and notifications.
- V5: per-clinic data keys (`clinic_key`), wrapped by the master key.

**Conventions.** IDs are UUIDv7. Money is stored as integer paise (`long`) and never as a float. Timestamps are UTC. Payments, chart records and audit events are append-only: a refund is a new negative entry, never an update.

## 7. Web service

`docs/openapi.yaml` is the contract. The Gradle `openApiGenerate` task produces the Retrofit client before every build. OkHttp adds the access token (`AuthInterceptor`). On a 401, `TokenAuthenticator` refreshes once and retries. If the server detects that a refresh token was reused, it revokes the whole token family and the user is logged out.

Sync works as follows. Local writes go into `pending_op`. `SyncWorker` pushes them in order, and then pulls changes since the stored cursor. The server applies each operation in its own transaction, so one conflict (for example, a slot that was booked on another device) is reported back without undoing the rest of the batch.

## 8. Testing

| Suite | Tool | What it covers | Result |
|---|---|---|---|
| Android unit (11 classes, 35 tests) | JUnit, Robolectric, Room in-memory | DAOs, repositories, ledger maths, money, sync engine, CSV export, PDF generation, ViewModels, chart hit-testing, platform receivers, app lock | **35/35 pass** (19 Oct 2026) |
| Android migration | Room `MigrationTestHelper` | Room 1 → 2 | in `androidTest` |
| Backend integration (13 classes, 38 tests) | JUnit 5, Testcontainers (PostgreSQL) | Auth flow and refresh rotation, tenant isolation (clinic A cannot see B), 100-thread double-booking, sync, billing idempotency, clinical, outbox, OpenAPI export, Modulith boundaries, per-clinic encryption keys, audit reads and chain verification | **38/38 pass** (19 Oct 2026) |
| Restore drill | `infra/restore-drill.sh` | A backup restores into a fresh database with matching row counts and audit-chain digest; a wrong passphrase, a tampered file and missing data are all rejected | **Pass** (19 Oct 2026) |
| Dependency scan | Trivy on `gradle.lockfile` | Known HIGH/CRITICAL vulnerabilities in shipped dependencies | **0 open** (19 Oct 2026) |

The key test is `BookingTest.hundredConcurrentBookingsExactlyOneWins`. It fires 100 concurrent requests for the same dentist and slot and asserts that exactly one succeeds. The database exclusion constraint guarantees this, not application code.

## 9. Security and privacy

- Patient data never appears in logs, SMS text or push text. FCM messages are data-only ("please sync").
- Permissions are checked on the server for every endpoint. The UI only hides buttons.
- Tenant isolation is enforced by PostgreSQL RLS, which applies even if a query forgets its `WHERE clinic_id`.
- Secrets come from environment variables (`.env.example`). The app uses dummy data only.

### 9.1 Security hardening (milestone M7)

| Control | How it works | Code | Evidence |
|---|---|---|---|
| Field-level encryption | AES-256-GCM on phone, email, address, history, allergies and visit notes. Each clinic has its own data key, stored in `clinic_key` and wrapped by the master key (`FIELD_ENC_KEY`, standing in for a KMS). The clinic id is bound into each ciphertext, so a value copied into another clinic's row fails to decrypt. | `shared/FieldCrypto.java`, `V5__clinic_keys.sql` | `FieldCryptoTest` |
| Blind index | HMAC-SHA256 of the last 10 digits lets staff search by phone without decrypting every row | `FieldCrypto.phoneIndex` | `PatientApiTest` |
| Hash-chained audit log | Every write and every clinical read is logged. Each row's SHA-256 covers the previous row's hash. The app role cannot UPDATE or DELETE audit rows. Owners can list the log (`GET /audit-events`) and re-verify the chain (`GET /audit-events/verify`). | `audit/AuditService.java`, `ReadAudit.java`, `AuditController.java` | `AuditTest` (an edited row is detected) |
| Device token storage | Tokens are kept in EncryptedSharedPreferences backed by the Android Keystore | `data/local/TokenStore.java` | — |
| Biometric lock | Optional, in Settings. Locks on a cold start and after 60 s in the background. Accepts fingerprint, face or device PIN. | `data/local/AppLock.java`, `ui/MainActivity.java` | `AppLockTest` |
| `FLAG_SECURE` | Every screen with patient data blocks screenshots and the recent-apps preview | `ui/MainActivity.java` | — |
| Encrypted backups | Nightly `pg_dump` encrypted with gpg AES-256, plus a monthly restore drill into a throwaway database that checks every table's row count and the audit-chain digest | `infra/backup.sh`, `restore.sh`, `restore-drill.sh`, `docs/runbook-backup.md` | Drill passed 28/28. It fails on a wrong passphrase, a tampered file and missing data. |
| Dependency scan | Locked runtime dependencies (`gradle.lockfile`) are scanned by Trivy in CI, failing on HIGH or CRITICAL | `build.gradle`, `.github/workflows/ci.yml`, `.trivyignore` | 0 open HIGH/CRITICAL (19 Oct 2026) |

The first scan found 11 issues. Nine were fixed by upgrading Jackson (2.21.7), Tomcat (10.1.61) and the PostgreSQL driver (42.7.12). Two Spring MVC issues are fixed only in Spring Framework 7 (Spring Boot 4). They affect XSLT views and Server-Sent Events, neither of which the API uses, so they are recorded with that reason in `.trivyignore` until the Boot 4 upgrade.

## 10. Limitations and future work

- **FCM on the device is not wired up.** The backend sends data-only FCM, but the app has no `google-services.json` (it would be a project secret) and no messaging service. Sync still runs on WorkManager's schedule.
- **Maps** use osmdroid (OpenStreetMap), so no API key is needed. **Navigate** hands off to whichever maps app is installed.
- **Backups stay on the database host.** Copying them to a second region and WAL archiving (needed for the 15-minute RPO) are not automated yet, so the current RPO is 24 hours (`docs/runbook-backup.md`).
- **Audit truncation.** The hash chain catches edited or deleted rows in the middle. Dropping the newest rows can only be caught by comparing the head hash with a copy kept elsewhere.
- **Blind indexes use one app-wide key.** Per-clinic index keys would need every phone re-indexed.
- Planned for M8 and M9: load tests, an Espresso UI suite, R8 and a signed release bundle.

## 11. How to run

```bash
# App only (offline demo)
./gradlew :android:app:installDebug          # then tap "Try the offline demo"

# Full stack
docker compose -f infra/docker-compose.yml up -d
./gradlew :backend:bootRun                   # API on :8080, Swagger UI at /swagger-ui.html
./gradlew :android:app:installDebug          # emulator reaches the PC at 10.0.2.2:8080

# Tests
./gradlew :android:app:testDebugUnitTest
./gradlew :backend:test                      # needs Docker for Testcontainers
```

## 12. Conclusion

DentaCloud covers all 20 lab experiments and the 5 course objectives inside one working app rather than 20 separate exercises. It also goes beyond the syllabus: offline-first sync, a custom Canvas dental chart, a database-level guarantee against double-booking, an append-only payment ledger and multi-tenant row-level security.
