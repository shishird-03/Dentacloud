# DentaCloud PRD: Multi-Clinic Dental Practice SaaS (Native Android + Java)

Version 1.0 · 18 Oct 2026 · Working name "DentaCloud" (replace before launch)

> **How to use this file with Claude Code.** Put this file at the repo root as `PRD.md`. Section 22 contains a ready-to-paste `CLAUDE.md`, the repo layout, milestone-by-milestone build order with acceptance criteria, and starter prompts. Build milestone by milestone; do not ask Claude Code to build the whole product in one go.

---

## 1. Product overview

DentaCloud is a multi-clinic SaaS that lets small and mid-size dental clinics run appointments, dental charting, treatment plans, billing, prescriptions and patient reminders from a fast **native Android app** (phone and tablet), backed by a multi-tenant **Spring Boot** API.

**Problem.** Most Indian dental clinics run on paper, Excel and WhatsApp. Existing dental apps cap dentists at 4, lock features to web-only or app-only, have weak security, and feel slow on tablets and low-end phones.

**Solution.** One Android app that works offline, unlimited dentists per clinic, a chairside-fast dental chart, an automatic recall engine, and real security (field encryption, audit log, tested backups).

**Target market.** Solo dentists and clinics with 1 to 10 staff in India first. Buyers are clinic owners. Daily users are receptionists, dentists and assistants.

**Positioning.** Lightweight, fast, affordable, trustworthy with patient data.

**Delivery context.** The product is built by a team of 3 as a college Mobile Application Development (CS3604-1) mini project that is then extended toward production. Section 5 maps the MAD lab syllabus onto product features so the same work satisfies both.

---

## 2. Goals, non-goals and success metrics

**Goals**

- Onboard a new clinic in under 10 minutes with no training call.
- Make a receptionist's busiest tasks (book, check in, bill) take 3 taps or fewer.
- Run smoothly on a 3 GB RAM Android phone and on a tablet at the chair.
- Work offline for schedule, patient lookup, notes and billing entry, and sync when online.
- Keep each clinic's data fully isolated and encrypted.

**Non-goals for MVP**

- iOS app, public web app, patient portal, WhatsApp Business integration.
- Insurance claim adjudication, full accounting, payroll.
- DICOM viewer (store and view image files only).
- Multi-branch chains and public API (V3).

**Success metrics**

| Metric | Target |
| --- | --- |
| Time to first appointment booked after signup | under 10 min |
| Weekly active users per clinic | 80% of staff accounts |
| p95 API latency | under 300 ms |
| Android cold start (mid-range phone) | under 2 s |
| Crash-free sessions | 99.5% or higher |
| Reminder delivery success | 98% or higher |
| App size | under 40 MB |
| No-show reduction for clinics | 25% lower within 3 months |
| Paying clinics | 20 by month 3 after launch, 100 by month 12 |

---

## 3. Users, personas and permissions

**Personas**

- **Dr. Meera, owner and dentist.** Wants daily revenue, fewer no-shows, trustworthy data. Uses a tablet at the chair and a phone at home.
- **Anita, receptionist.** Books, reschedules, bills, messages patients all day. Needs speed.
- **Ravi, associate dentist.** Needs his own calendar, chart and treatment plans.
- **Suresh, dental assistant.** Prepares the chair, updates visit status, uploads photos and X-rays.
- **Accountant.** Billing, expenses and reports.
- **Patient (V3).** Views appointments, prescriptions and invoices.

**Permission matrix** (R = read, W = write, A = approve or delete, blank = no access)

| Capability | Owner | Dentist | Receptionist | Assistant | Accountant |
| --- | --- | --- | --- | --- | --- |
| Clinic settings, staff, plans | A | | | | |
| Patient demographics | W | W | W | R | R |
| Appointments | W | W | W | W (status) | R |
| Dental chart and clinical notes | W | W | | R | |
| Treatment plans and estimates | W | W | R | R | R |
| Prescriptions | W | W | | | |
| Images and X-rays | W | W | R | W | R |
| Invoices and payments | W | R | W | R | W |
| Refunds and large discounts | A | | | | W |
| Expenses | W | | | | W |
| Reports and dashboards | R | R (own) | R (daily) | | R |
| Audit log | R | | | | R |
| Data export and backups | A | | | | |

Permissions are stored as named scopes (for example `patient.read`, `chart.write`, `invoice.refund`) so custom roles need no code change. They are enforced **on the server**; the app only hides UI.

---

## 4. Scope and release phases

| Phase | Window | Contents |
| --- | --- | --- |
| **MVP** | Weeks 1 to 14 | Clinic signup and onboarding, staff and roles, patients, appointments and calendar, dental chart (FDI), visit notes, treatment plans, invoices and payments, receipt and prescription PDFs, SMS and push reminders, dashboard, audit log, encryption, backups, offline mode with sync, Android app (phone and tablet) |
| **V2** | Months 5 to 7 | X-ray and photo management with tooth tagging, expenses and profit report, recall campaigns, inventory and lab orders, waitlist, subscriptions via Razorpay, React admin web portal, WhatsApp messaging |
| **V3** | Months 8 to 12 | iOS app, patient portal and booking page, multi-branch and chains, analytics, public API and webhooks, AI assistant (note drafting, no-show prediction), localisation |

**Mini-project checkpoint:** the end of build milestone M6 (Section 22) is the "syllabus submission complete" point. Milestones M7 to M9 are the production extension.

---

## 5. MAD lab syllabus mapping (CS3604-1)

The syllabus is native Android with Java, Android Studio, SQLite, services, receivers, notifications, web service and maps. The product is designed so every experiment appears as a real feature.

**Part A**

| # | Syllabus experiment | Where it appears in the product |
| --- | --- | --- |
| 1 | Checkboxes and Toast total | Select procedures on a visit and show the estimated total |
| 2 | Calculator (ADD, SUB, MUL, DIV, CLEAR) | Billing screen: amount, discount, tax, balance |
| 3 | Random color on click | Color labels for appointment chips and patient cards |
| 4 | Options menu, two activities | Top menu switching between Patients and Appointments |
| 5 | Context menu with Intent | Long-press patient: edit, delete, change card color |
| 6 | SMS using Intent | Send appointment reminder from the patient profile |
| 7 | Phone call | One-tap call from the patient profile |
| 8 | Date picker and spinner, Toast | Booking: choose date and procedure |
| 9 | Confirmation dialog | Confirm cancel, delete, refund |
| 10 | Camera capture as background | Intraoral photo capture; also clinic logo or profile photo |

**Part B**

| # | Syllabus experiment | Where it appears in the product |
| --- | --- | --- |
| 1 | Service playing in background | Foreground service that watches upcoming appointments |
| 2 | AsyncTask 1 to 1000 with progress bar | Export or backup progress (implemented with ExecutorService; keep a separate lab-only AsyncTask demo screen) |
| 3 | Battery broadcast receiver | Low-battery warning banner on the tablet dashboard |
| 4 | Insert and display all DB values | Patient registration and list |
| 5 | Search by USN, then call | Search patient by phone or ID, then call |
| 6 | Two-activity resume builder | Two-step patient registration form with photo |
| 7 | Notification launches another activity | Reminder notification opens the appointment detail |
| 8 | WebView, two activities | Dental care tips page or clinic website |
| 9 | SharedPreferences save, delete, retrieve | Login session, clinic settings, UI preferences |
| 10 | Animations (blink, move, rotate, zoom) | Splash, tooth highlight blink, chart zoom |

**Course objectives**

| Objective | Product feature |
| --- | --- |
| 1. Android architecture | Documented in the report: MVVM, components, lifecycle |
| 2. UI components and Android components | All screens, services, receivers, providers |
| 3. Data handling with SQLite | Room (SQLite) offline database |
| 4. Android web service | Retrofit client for the Spring Boot REST API |
| 5. Google Maps and navigation | Clinic location screen with "Navigate" button |

**Beyond the syllabus** (what makes it production grade): Room migrations, Retrofit with JWT refresh, MVVM with Hilt, WorkManager sync, Firebase Cloud Messaging, Keystore and biometric lock, custom Canvas dental chart, CameraX, PDF generation, conflict-aware sync, Crashlytics, R8 and signed app bundle, JUnit/Mockito/Espresso tests.

---

## 6. Functional requirements

Priority: P0 = MVP, P1 = V2, P2 = V3.

| ID | Module | Requirement | Priority |
| --- | --- | --- | --- |
| F1 | Onboarding | Signup with phone OTP or email, create clinic, add logo, working hours, chairs, staff in a 4-step wizard | P0 |
| F2 | Identity | Login with JWT access plus rotating refresh tokens, optional MFA for owner and dentists, device list and remote logout, biometric app lock | P0 |
| F3 | Patients | Register with phone, DOB, gender, address, medical history, allergies; search by name or phone under 200 ms; duplicate warning; family linking | P0 |
| F4 | Appointments | Day and week views by dentist or chair; reschedule; types with default durations; conflict prevention per dentist and chair; walk-in queue; statuses (booked, arrived, in chair, done, no-show, cancelled) | P0 |
| F5 | Dental chart | FDI odontogram, per-surface conditions, per-tooth history, adult and child dentition, chart snapshot per visit | P0 |
| F6 | Visits | Complaint, diagnosis, procedures done, notes, templates and quick phrases | P0 |
| F7 | Treatment plans | Multi-visit plans with items per tooth, estimates, accept or defer, progress | P0 |
| F8 | Billing | Procedure price list, invoices from plan items, discounts, tax, partial payments, installments, cash, UPI and card modes, refunds, PDF receipts | P0 |
| F9 | Prescriptions | Drug catalog, favourites, templates, PDF with clinic letterhead, share via Android share sheet | P0 |
| F10 | Reminders | FCM push and SMS reminders at 24 h and 2 h, templates, delivery status, opt-out | P0 |
| F11 | Dashboard | Today's appointments, collections, pending dues, new patients | P0 |
| F12 | Audit and backup | Append-only audit log; encrypted daily backups; owner export | P0 |
| F13 | Offline and sync | Read and write core data offline, push and pull sync, visible sync status | P0 |
| F14 | Maps | Clinic location and navigation intent | P0 (syllabus) |
| F15 | Images | Capture or upload X-rays and photos, thumbnails, tooth tagging, before and after compare | P1 |
| F16 | Recall engine | Rules such as 6-month scaling and RCT follow-up generate call lists and messages | P1 |
| F17 | Expenses | Categories, recurring items, monthly profit and loss | P1 |
| F18 | Inventory and labs | Stock levels, low-stock alerts, lab work orders | P1 |
| F19 | Subscriptions | Plan selection, trial, Razorpay billing, GST invoice for the SaaS fee | P1 |
| F20 | Admin web portal | React portal for reception desktops and owner reports | P1 |
| F21 | Patient portal | Appointments, prescriptions, invoices, consent | P2 |
| F22 | Multi-branch | Branches under one clinic account | P2 |
| F23 | Public API | API keys, webhooks, OpenAPI docs | P2 |

**Key acceptance rules**

- Two staff booking the same dentist or chair for overlapping times must never both succeed, even under simultaneous requests (enforced by a PostgreSQL exclusion constraint, not only app code).
- Every create, update and delete on clinical or financial data writes an audit event (actor, time, device, before and after).
- Money is stored as integer paise. Payments and refunds are append-only ledger entries, never edited.
- Clinical chart records are append-only history; "current state" is derived.
- No patient data appears in logs, SMS text or push notification bodies.

---

## 7. Tech stack

| Layer | Choice | Reason |
| --- | --- | --- |
| Android language | **Java 17** source compatibility, Android Studio | Syllabus requirement, team strength |
| Android UI | XML layouts, Material Components, ViewBinding, Navigation component (single activity), RecyclerView + DiffUtil | Matches syllabus; fast lists |
| Android architecture | MVVM: ViewModel, LiveData, Repository | Testable and explainable |
| Dependency injection | Hilt | Works with Java |
| Local database | Room over SQLite, with migrations | Offline source of truth (syllabus: SQLite) |
| Networking | Retrofit 2, OkHttp, Gson or Moshi, Authenticator for token refresh | Syllabus: web service |
| Background | WorkManager (sync, uploads), Foreground Service (reminders), BroadcastReceivers | Syllabus + reliability |
| Push | Firebase Cloud Messaging | Reminders and slot-change alerts |
| Security (device) | Android Keystore, EncryptedSharedPreferences, BiometricPrompt | Token and data protection |
| Images | CameraX, Glide, signed-URL uploads | Photos and X-rays |
| PDF | Android `PdfDocument` (on device) and OpenPDF (server) | Receipts and prescriptions |
| Maps | osmdroid (no key) or Google Maps SDK | Syllabus objective 5 |
| Crash and perf | Firebase Crashlytics | Production visibility |
| Backend language | Java 21 | Virtual threads for many concurrent users |
| Backend framework | Spring Boot 3, Spring Modulith, Spring Security, Spring Data JPA, Flyway, MapStruct, Bean Validation, springdoc-openapi | Productive and auditable |
| Database | PostgreSQL 16 with row-level security | Tenant isolation, exclusion constraints |
| Cache and rate limit | Redis | Sessions, rate limits, caching |
| Async | Postgres outbox table plus worker (RabbitMQ optional later) | Reliable reminders without heavy infra |
| Object storage | S3 or MinIO with signed URLs | Images and PDFs off the API |
| Messaging | MSG91 or Twilio (SMS), FCM (push), SES (email) | Delivery with fallbacks |
| SaaS billing | Razorpay subscriptions (V2) | UPI and cards in India |
| Infra | Docker, AWS (ECS or EC2, RDS PostgreSQL, ElastiCache, S3, KMS) or a VPS for early stage | Managed and scalable |
| CI/CD | GitHub Actions, Testcontainers, Gradle | Automated safety net |
| Observability | Micrometer, Prometheus, Grafana, Sentry, Uptime Kuma | Metrics and alerts |

**API contract:** OpenAPI 3 is the single source of truth. Generate the Android Retrofit interfaces and DTOs from it (OpenAPI Generator, Java + Retrofit), and fail CI if the generated client no longer compiles.

---

## 8. System architecture and multi-tenancy

```
Android app (Java, MVVM)
  UI (XML, Material) -> ViewModel + LiveData -> Repository
                                  |- Room (SQLite, offline source of truth)
                                  '- Retrofit + OkHttp (JWT, refresh)
  WorkManager (sync, uploads) | Foreground Service | Receivers | FCM
        | HTTPS
CDN / Load balancer / TLS / per-clinic rate limit
        |
Spring Boot modular monolith (stateless)
  identity | patient | scheduling | clinical | billing | prescription
  imaging | notification | reports/audit | sync | subscription
        |
PostgreSQL 16 (RLS, outbox) | Redis | S3/MinIO | KMS
        |
Workers (reminders, PDFs, exports) -> SMS / email / FCM / Razorpay
```

**Multi-tenancy:** shared database, shared schema, `clinic_id` on every tenant table, PostgreSQL row-level security. Schema-per-clinic or database-per-clinic are future options for enterprise customers.

**Rules**

- The tenant is taken only from the signed JWT, never from a request parameter. A transaction filter runs `SET LOCAL app.clinic_id = '<uuid>'` on every request; background jobs set it explicitly.
- Each backend module owns its tables and talks to others through service interfaces and events (`AppointmentBooked`, `InvoicePaid`). Spring Modulith tests fail the build on illegal module access.
- App instances are stateless; sessions and rate limits live in Redis.
- Slow work (reminders, PDFs, exports) goes through the outbox and workers, never on request threads.

---

## 9. Android app design

**Package structure** (`com.dentacloud.app`)

```
app/
  App.java                      (Hilt application)
  di/                           (Hilt modules: network, database, repositories)
  data/
    local/                      (Room: AppDatabase, entities, DAOs, migrations)
    remote/                     (Retrofit API, DTOs, AuthInterceptor, TokenAuthenticator)
    repo/                       (repositories combining local + remote)
    sync/                       (SyncWorker, PendingOp, ConflictResolver)
  domain/model/                 (plain Java models)
  ui/
    auth/ onboarding/ dashboard/ patients/ calendar/ chart/
    visits/ plans/ billing/ prescriptions/ images/ maps/ settings/
    common/                     (base fragments, adapters, dialogs)
  service/                      (ReminderForegroundService, FcmService)
  receiver/                     (BatteryReceiver, BootReceiver, ReminderReceiver)
  util/                         (Money, PdfGenerator, Crypto, Biometric, Uuid7)
```

**Single-activity** with the Navigation component. Phone uses bottom navigation (Today, Patients, Calendar, More). Tablet (width 600dp or more) uses a navigation rail and two-pane layouts (list and detail).

**Data flow:** screens read from Room via LiveData. Writes go to Room first, add a row to the `pending_op` table, and WorkManager pushes pending operations when online. This makes the app feel instant and works offline.

**Sync design**

- Client generates UUIDv7 ids (use a library such as `uuid-creator`), so records can be created offline without collisions.
- `GET /sync/pull?since=<seq>` returns changes after a server sequence number; `POST /sync/push` sends a batch of operations, each with an `Idempotency-Key`.
- Conflict rules: append-only data (chart records, payments, audit) never conflicts; appointments are server-wins with a clear "slot taken" message and an option to rebook; notes are last-writer-wins with version history kept on the server.
- Sync status chip always visible: Synced, Syncing, Offline (N pending), Needs attention.

**Dental chart:** a custom `View` that draws the FDI odontogram on a `Canvas`. Tap tooth, then surface (M, O, D, B, L), then condition. Supports pinch zoom, pan, adult and child dentition, color by condition, and a history slider. Cache drawn paths; avoid allocations inside `onDraw`.

**Reminders:** server pushes FCM at T-24 h and T-2 h. On device, a foreground service (and WorkManager as fallback) shows local notifications for today's appointments; tapping opens the appointment detail.

**Security on device:** tokens in EncryptedSharedPreferences backed by Keystore, optional biometric unlock, `FLAG_SECURE` on patient screens, no PHI in logs or notification text, backups disabled for sensitive files (`android:allowBackup="false"` or explicit rules), R8 shrinking.

**Performance rules:** no work on the main thread; RecyclerView with DiffUtil and stable ids; paging for long lists; thumbnails via Glide; cold start under 2 s; test on a 3 GB RAM device every sprint.

---

## 10. Database schema (PostgreSQL)

Conventions on every tenant table: `id uuid` (UUIDv7), `clinic_id uuid not null`, `created_at`, `updated_at`, `created_by`, `deleted_at` (soft delete), `version int` (optimistic locking and sync). Timestamps are `timestamptz` in UTC. Money is integer paise.

**Tenant isolation (applied to every tenant table)**

```sql
ALTER TABLE patient ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON patient
  USING (clinic_id = current_setting('app.clinic_id')::uuid);
-- API sets per request: SET LOCAL app.clinic_id = '<clinic uuid from JWT>';
```

**Users, tenants and access**

```sql
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE clinic (
  id uuid PRIMARY KEY, name text NOT NULL, slug text UNIQUE NOT NULL,
  phone text, email text, address jsonb, gstin text,
  timezone text NOT NULL DEFAULT 'Asia/Kolkata', currency char(3) NOT NULL DEFAULT 'INR',
  plan_id uuid, status text NOT NULL DEFAULT 'TRIAL',  -- TRIAL, ACTIVE, PAST_DUE, SUSPENDED
  trial_ends_at timestamptz, settings jsonb NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
  id uuid PRIMARY KEY, email citext UNIQUE, phone text UNIQUE,
  password_hash text,                       -- Argon2id; null if OTP-only
  full_name text NOT NULL, mfa_enabled boolean NOT NULL DEFAULT false,
  mfa_secret_enc bytea, status text NOT NULL DEFAULT 'ACTIVE',
  last_login_at timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE role (
  id uuid PRIMARY KEY, clinic_id uuid,      -- null = system role
  name text NOT NULL, is_system boolean NOT NULL DEFAULT false
);
CREATE TABLE role_permission (
  role_id uuid REFERENCES role(id), scope text NOT NULL,   -- e.g. 'chart.write'
  PRIMARY KEY (role_id, scope)
);

-- A person can belong to several clinics (e.g. a visiting dentist)
CREATE TABLE clinic_member (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL REFERENCES clinic(id),
  user_id uuid NOT NULL REFERENCES app_user(id),
  role_id uuid NOT NULL REFERENCES role(id),
  is_dentist boolean NOT NULL DEFAULT false,
  registration_no text, color text, status text NOT NULL DEFAULT 'ACTIVE',
  UNIQUE (clinic_id, user_id)
);

CREATE TABLE refresh_token (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_user(id),
  token_hash text NOT NULL, family_id uuid NOT NULL,       -- rotation and reuse detection
  device_name text, device_id text, fcm_token text, ip inet,
  expires_at timestamptz NOT NULL, revoked_at timestamptz
);

CREATE TABLE chair (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, name text NOT NULL, active boolean DEFAULT true
);
```

**Patients and scheduling**

```sql
CREATE TABLE patient (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_no text NOT NULL,
  full_name text NOT NULL, dob date, gender text,
  phone_enc bytea, phone_blind_idx bytea,                  -- searchable via HMAC
  email_enc bytea, address_enc bytea,
  medical_history_enc bytea, allergies_enc bytea,          -- field-level encrypted
  family_head_id uuid REFERENCES patient(id),
  marketing_opt_in boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz, version int NOT NULL DEFAULT 0,
  UNIQUE (clinic_id, patient_no)
);
CREATE INDEX ON patient (clinic_id, phone_blind_idx);
CREATE INDEX ON patient USING gin (full_name gin_trgm_ops);

CREATE TABLE appointment (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_id uuid NOT NULL REFERENCES patient(id),
  dentist_id uuid NOT NULL REFERENCES clinic_member(id), chair_id uuid REFERENCES chair(id),
  type_id uuid, start_at timestamptz NOT NULL, end_at timestamptz NOT NULL,
  status text NOT NULL DEFAULT 'BOOKED', reason text, source text,   -- app, walk-in, phone
  version int NOT NULL DEFAULT 0, created_by uuid,
  created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (end_at > start_at),
  EXCLUDE USING gist (dentist_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (status NOT IN ('CANCELLED','NO_SHOW')),
  EXCLUDE USING gist (chair_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (chair_id IS NOT NULL AND status NOT IN ('CANCELLED','NO_SHOW'))
);
CREATE INDEX ON appointment (clinic_id, start_at);
```

**Clinical records**

```sql
CREATE TABLE visit (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_id uuid NOT NULL, appointment_id uuid,
  dentist_id uuid NOT NULL, visited_at timestamptz NOT NULL DEFAULT now(),
  complaint_enc bytea, diagnosis_enc bytea, notes_enc bytea, dek_id uuid NOT NULL
);

CREATE TABLE tooth_record (                 -- append-only chart history
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_id uuid NOT NULL, visit_id uuid,
  tooth_fdi smallint NOT NULL CHECK (tooth_fdi BETWEEN 11 AND 85),
  surface text CHECK (surface IN ('M','O','D','B','L')),
  condition text NOT NULL, note text, recorded_by uuid NOT NULL,
  recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON tooth_record (clinic_id, patient_id, tooth_fdi, recorded_at DESC);

CREATE TABLE procedure_catalog (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, code text, name text NOT NULL,
  default_paise bigint NOT NULL, default_minutes int NOT NULL DEFAULT 30,
  tax_percent numeric(5,2) DEFAULT 0
);

CREATE TABLE treatment_plan (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_id uuid NOT NULL, title text,
  status text NOT NULL DEFAULT 'PROPOSED',   -- PROPOSED, ACCEPTED, IN_PROGRESS, DONE
  estimate_paise bigint NOT NULL DEFAULT 0, created_by uuid, created_at timestamptz DEFAULT now()
);
CREATE TABLE plan_item (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, plan_id uuid NOT NULL REFERENCES treatment_plan(id),
  procedure_id uuid NOT NULL, tooth_fdi smallint, surface text, est_paise bigint NOT NULL,
  status text NOT NULL DEFAULT 'PLANNED', sequence int, done_visit_id uuid
);

CREATE TABLE prescription (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_id uuid NOT NULL, visit_id uuid,
  dentist_id uuid NOT NULL, issued_at timestamptz DEFAULT now(), notes text
);
CREATE TABLE prescription_item (
  id uuid PRIMARY KEY, prescription_id uuid NOT NULL REFERENCES prescription(id),
  drug_name text NOT NULL, dose text, frequency text, duration_days int, instructions text
);

CREATE TABLE patient_image (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_id uuid NOT NULL, visit_id uuid,
  storage_key text NOT NULL, thumb_key text, kind text,      -- IOPA, OPG, PHOTO
  tooth_fdi smallint, taken_at timestamptz, uploaded_by uuid, size_bytes int
);
```

**Billing (append-only ledger)**

```sql
CREATE TABLE invoice (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_id uuid NOT NULL, invoice_no text NOT NULL,
  subtotal_paise bigint NOT NULL, discount_paise bigint NOT NULL DEFAULT 0,
  tax_paise bigint NOT NULL DEFAULT 0, total_paise bigint NOT NULL,
  status text NOT NULL DEFAULT 'ISSUED',     -- ISSUED, PARTIAL, PAID, VOID
  issued_at timestamptz DEFAULT now(), UNIQUE (clinic_id, invoice_no)
);
CREATE TABLE invoice_line (
  id uuid PRIMARY KEY, invoice_id uuid NOT NULL REFERENCES invoice(id),
  plan_item_id uuid, description text NOT NULL, qty int NOT NULL DEFAULT 1, amount_paise bigint NOT NULL
);
CREATE TABLE payment_entry (                 -- never updated or deleted
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, invoice_id uuid NOT NULL REFERENCES invoice(id),
  kind text NOT NULL CHECK (kind IN ('PAYMENT','REFUND')),
  mode text NOT NULL,                        -- CASH, UPI, CARD, BANK
  amount_paise bigint NOT NULL CHECK (amount_paise > 0), reference text,
  idempotency_key text NOT NULL, recorded_by uuid NOT NULL, recorded_at timestamptz DEFAULT now(),
  UNIQUE (clinic_id, idempotency_key)
);
CREATE TABLE expense (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, category text NOT NULL, amount_paise bigint NOT NULL,
  spent_on date NOT NULL, notes text, recurring_rule jsonb
);
```

**Messaging, audit, subscriptions, sync, keys**

```sql
CREATE TABLE outbox_event (
  id uuid PRIMARY KEY, clinic_id uuid, topic text NOT NULL, payload jsonb NOT NULL,
  status text NOT NULL DEFAULT 'PENDING', attempts int NOT NULL DEFAULT 0,
  run_after timestamptz NOT NULL DEFAULT now(), created_at timestamptz DEFAULT now()
);
CREATE INDEX ON outbox_event (status, run_after);   -- worker uses FOR UPDATE SKIP LOCKED

CREATE TABLE notification (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, patient_id uuid,
  channel text NOT NULL,                      -- SMS, PUSH, EMAIL
  template_key text NOT NULL, status text NOT NULL, provider_ref text, error text,
  scheduled_for timestamptz
);
CREATE TABLE recall_rule (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, procedure_id uuid,
  after_days int NOT NULL, template_key text NOT NULL, active boolean DEFAULT true
);

CREATE TABLE audit_event (                    -- append-only, hash-chained
  id bigserial PRIMARY KEY, clinic_id uuid NOT NULL, actor_id uuid, action text NOT NULL,
  resource_type text NOT NULL, resource_id uuid, ip inet, device text,
  before jsonb, after jsonb, occurred_at timestamptz NOT NULL DEFAULT now(),
  prev_hash bytea, hash bytea NOT NULL
);
REVOKE UPDATE, DELETE ON audit_event FROM app_role;

CREATE TABLE plan (
  id uuid PRIMARY KEY, code text UNIQUE NOT NULL, name text NOT NULL, price_paise bigint NOT NULL,
  limits jsonb NOT NULL                       -- e.g. {"dentists":3,"sms_per_month":500,"storage_gb":5}
);
CREATE TABLE subscription (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL UNIQUE, plan_id uuid NOT NULL, provider_sub_id text,
  status text NOT NULL, current_period_end timestamptz, cancel_at_period_end boolean DEFAULT false
);

CREATE TABLE sync_change (                    -- powers mobile sync
  seq bigserial PRIMARY KEY, clinic_id uuid NOT NULL, entity text NOT NULL,
  entity_id uuid NOT NULL, op text NOT NULL, changed_at timestamptz DEFAULT now()
);
CREATE INDEX ON sync_change (clinic_id, seq);

CREATE TABLE encryption_key (
  id uuid PRIMARY KEY, clinic_id uuid NOT NULL, wrapped_dek bytea NOT NULL,
  kek_id text NOT NULL, version int NOT NULL, status text NOT NULL
);
```

**Android Room tables (local mirror).** `patient`, `appointment`, `visit`, `tooth_record`, `treatment_plan`, `plan_item`, `procedure_catalog`, `invoice`, `invoice_line`, `payment_entry`, `prescription`, `prescription_item`, plus two local-only tables: `pending_op` (id, entity, entity_id, operation, payload_json, idempotency_key, created_at, attempts, last_error) and `sync_state` (clinic_id, last_seq, last_synced_at). Sensitive columns are stored encrypted or the database file itself is encrypted (SQLCipher) in the production phase. Every Room schema change ships with a tested migration.

---

## 11. API design

One versioned REST API at `/api/v1`, described by OpenAPI.

**Conventions**

- Auth: `Authorization: Bearer <JWT>`. The JWT carries `sub`, `clinic_id`, `role`, scopes and lives 15 minutes. Refresh tokens rotate on every use; reuse of an old token revokes the whole family.
- Pagination: cursor-based (`?limit=50&cursor=...`).
- Idempotency: `Idempotency-Key` header required on payments, invoices and appointment creation.
- Concurrency: `ETag` and `If-Match` or a `version` field; stale writes return 409.
- Errors: RFC 7807 problem JSON with a stable `code`.
- gzip compression and sparse fieldsets (`?fields=`).
- Rate limits per clinic and per user (Redis).

**Endpoint groups**

| Group | Examples |
| --- | --- |
| Auth | `POST /auth/login`, `/auth/otp/verify`, `/auth/refresh`, `/auth/logout` |
| Clinic | `GET/PATCH /clinic`, `/clinic/members`, `/clinic/roles`, `/clinic/chairs` |
| Patients | `GET /patients?q=`, `POST /patients`, `GET/PATCH /patients/{id}`, `GET /patients/{id}/timeline` |
| Appointments | `GET /appointments?from&to&dentistId`, `POST /appointments`, `PATCH /appointments/{id}/status`, `POST /appointments/{id}/reschedule` |
| Clinical | `GET /patients/{id}/chart`, `POST /patients/{id}/chart/records`, `POST /visits`, `GET /visits/{id}` |
| Treatment | `POST /treatment-plans`, `PATCH /plan-items/{id}`, `POST /treatment-plans/{id}/invoice` |
| Billing | `POST /invoices`, `POST /invoices/{id}/payments`, `POST /invoices/{id}/refunds`, `GET /invoices/{id}/pdf` |
| Prescriptions | `POST /prescriptions`, `GET /prescriptions/{id}/pdf` |
| Images | `POST /images/upload-url`, `POST /images/confirm`, `GET /patients/{id}/images` |
| Devices | `POST /devices/fcm-token` |
| Reports | `GET /reports/daily-collection`, `/reports/dues`, `/reports/appointments` |
| Admin | `GET /audit-events`, `POST /admin/export`, `POST /admin/backups` |
| Sync | `GET /sync/pull?since=`, `POST /sync/push` |
| Subscription (V2) | `GET /subscription`, `POST /subscription/checkout`, `POST /webhooks/razorpay` |

**Realtime:** FCM data messages tell devices to pull ("appointment changed", "patient arrived"). A WebSocket or SSE channel is optional later for the web portal.

---

## 12. Design system and UX

The interface is calm, dense where staff need speed and spacious where the dentist needs focus. Every screen must feel instant, even on weak clinic Wi-Fi.

**Principles**

1. **Speed is a feature.** Render from Room immediately; refresh in the background.
2. **Three taps for the common jobs.** Book, check in, take payment, share prescription.
3. **One screen, one job.** Global search jumps to any patient.
4. **Chair-side first.** Touch targets of 48dp or more, one-hand phone use, tablet two-pane layouts, dark theme for dim rooms.
5. **Never lose work.** Autosave drafts, offline queue, clear sync status.
6. **Safe by design.** Confirm destructive actions; show who changed clinical data and when.

**Visual system:** Material Components theme with a neutral gray base, one calm teal or blue primary, and semantic colors for appointment status (booked, arrived, in chair, done, no-show). WCAG AA contrast. One typeface, 4dp spacing grid, 8dp corner radius. Tokens live in `res/values` (colors, dimens, styles) so the theme changes in one place.

**Key screens**

| Screen | Notes |
| --- | --- |
| Dashboard | Today's schedule, quick check-in, collection total, pending recalls, low-battery banner (tablet) |
| Calendar | Day and week views by dentist or chair, color-coded status, instant conflict hint |
| Patient profile | Header with allergies and alerts; tabs for timeline, chart, plans, billing, images |
| Dental chart | Canvas odontogram: tooth, surface, condition; zoom and pan; history slider |
| Billing | Invoice from plan items, split payments, calculator, receipt PDF |
| Maps | Clinic location with Navigate button |
| Settings | Clinic profile, staff, roles, templates, backup and sync status |

**Lightweight and smooth techniques:** RecyclerView with DiffUtil, paging, view binding, no nested scrolling lists, vector drawables, WebP images, lazy thumbnails, cached chart paths, R8 shrinking, baseline profiles for faster startup, and skeleton placeholders instead of spinners.

---

## 13. Non-functional requirements

| Area | Requirement |
| --- | --- |
| Availability | 99.9% monthly, zero-downtime deploys, health checks |
| API latency | p50 under 80 ms, p95 under 300 ms, p99 under 800 ms |
| Throughput | 300 req/s sustained on 2 to 3 app instances |
| Search | Patient search under 200 ms with 100,000 patients per clinic |
| Android | Cold start under 2 s, 60 fps scrolling, app size under 40 MB, minSdk 26 (Android 8) |
| Offline | Core flows usable offline; sync resumes automatically |
| Scalability | Stateless app servers, connection pooling (PgBouncer), read replica for reports, table partitioning for append-only tables past 50 million rows |
| Durability | RPO 15 min (WAL archiving), RTO 1 hour, monthly restore drill |
| Fairness | Per-clinic rate limits and query timeouts |
| Accessibility | TalkBack labels, WCAG 2.1 AA contrast, scalable text |
| Localisation | Timezone and currency per clinic; English first, Hindi and Kannada next |

**Performance engineering rules**

- Every hot-path query has an index and is checked with `EXPLAIN` in review; queries over 200 ms are logged.
- Cache clinic settings, procedure catalog and role scopes in Redis with short TTLs.
- Heavy work (PDFs, bulk messages, exports) runs on workers.
- k6 load tests for the top 10 endpoints; fail the release if p95 regresses by more than 20%.

---

## 14. Security, privacy and compliance

| Area | Requirement |
| --- | --- |
| Tenant isolation | `clinic_id` on every row, RLS, tenant from signed token only, automated cross-tenant tests in CI |
| Authentication | Argon2id passwords, phone OTP, optional TOTP MFA (required for owners and dentists on paid plans), lockout and rate limiting |
| Sessions | 15-minute access tokens, rotating refresh tokens with reuse detection, device list and remote logout |
| Authorization | Role scopes checked server-side on every endpoint, deny by default |
| Transit | TLS 1.2+, HSTS; optional certificate pinning in the app |
| At rest | Encrypted database and storage volumes; field-level AES-256-GCM for clinical notes, history, allergies, phone and address using per-clinic data keys wrapped by a KMS master key |
| Searchable encrypted fields | HMAC blind indexes for phone and ID lookup |
| Device | Keystore-backed token storage, biometric lock, `FLAG_SECURE`, no PHI in logs or notifications |
| Audit | Append-only hash-chained log of every clinical read and every write; owner can view and export |
| Backups | Encrypted daily snapshots plus continuous WAL archive in a separate region or account; monthly restore drill |
| Application security | OWASP ASVS L2 and MASVS checklists, input validation, parameterised queries, dependency scanning, secrets in a vault |
| Uploads | Type and size checks, virus scan, short-lived signed URLs |
| Pen test | Before public launch and yearly |

**Privacy and legal (India first)**

- Digital Personal Data Protection Act 2023: record consent, state purpose, support access, correction and erasure, and notify breaches. The clinic is the data fiduciary and DentaCloud is the processor; sign a data processing agreement with each clinic.
- Medical records have legal retention periods (check current Dental Council and state rules), so erasure means anonymisation plus retention locks, not blind deletion.
- Host data in India and say so in the privacy policy.
- SMS uses DLT-registered templates; honour opt-outs; keep health details out of SMS and push text.
- Google Play requires a privacy policy and a Data Safety form; prepare them early because health apps get closer review.
- Have counsel review the terms, privacy policy and DPA before launch. This PRD is not legal advice.

---

## 15. SaaS plans and subscriptions

Prices are placeholders to validate with 10 to 15 clinic interviews.

| Plan | Price per clinic per month (INR) | Dentists | Patients | SMS included | Storage |
| --- | --- | --- | --- | --- | --- |
| Starter | 499 | 1 | 1,000 | 100 | 2 GB |
| Pro | 1,499 | up to 5 | Unlimited | 500 | 20 GB |
| Clinic Plus | 3,499 | Unlimited | Unlimited | 2,000 | 100 GB |

**Rules:** 14-day free trial without a card; read-only mode (not deletion) after trial or non-payment with a 30-day grace period; plan limits live in `plan.limits` and are checked in one entitlement service; Razorpay subscriptions with idempotent webhooks; owner can always export data for 90 days after cancellation.

---

## 16. DevOps, observability and disaster recovery

- **Environments:** local (Docker Compose), staging (fake data only, never copy production data), production.
- **Pipeline:** pull request runs lint, unit tests, Testcontainers integration tests and dependency scan; merge builds the backend image and an Android release build; staging deploy runs smoke tests; production uses rolling deploys with backward-compatible Flyway migrations (expand, then contract).
- **Android release:** signed `.aab`, Play internal testing, then staged rollout (5%, 25%, 100%).
- **Topology (launch):** load balancer, 2 stateless Spring Boot tasks, managed PostgreSQL with PgBouncer, Redis, one worker task, S3, KMS. A single VPS with Docker Compose is acceptable for the first few clinics if backups and monitoring are in place.
- **Observability:** Micrometer to Prometheus and Grafana; structured JSON logs with trace ids and no patient data; Sentry or Crashlytics for crashes; alerts for error rate above 2%, p95 above 600 ms, queue backlog above 1,000, failed backup, certificate expiry.
- **DR:** continuous WAL archiving plus daily snapshots to a second location, 35-day retention, quarterly full restore drill with a written runbook, and an incident response plan covering breach notification.

---

## 17. Testing and quality

| Layer | Tools | What it proves |
| --- | --- | --- |
| Backend unit | JUnit 5, Mockito | Billing math, slot generation, permissions |
| Backend integration | Spring Boot Test, Testcontainers (PostgreSQL, Redis) | Queries, RLS, migrations, exclusion constraints |
| Tenant isolation | Dedicated suite | Clinic A can never read or write Clinic B data through any endpoint |
| Concurrency | 100-thread booking test | Double-booking is impossible |
| Contract | OpenAPI diff in CI, generated client compiles | App and backend do not drift |
| Android unit | JUnit, Mockito, Room in-memory tests | ViewModels, repositories, DAOs, migrations |
| Android UI | Espresso | Login, booking, billing flows |
| Load | k6 | 500 concurrent users, p95 under 300 ms |
| Security | OWASP ZAP, dependency scan, pen test | No known high-severity issues |
| Usability | 5 clinic staff per release, task timing | Three-tap goals are met |

**Beta program:** run the MVP free in 3 to 5 clinics for 6 weeks, alongside their old process, and track daily use, bugs per week and missed reminders. Every bug fix ships with a regression test.

---

## 18. Team and ownership (3 people)

| Person | Owns |
| --- | --- |
| **A: Backend and security** | Spring Boot, multi-tenancy, auth, sync API, encryption, audit, deployment, backups |
| **B: Android data and platform** | Room, repositories, Retrofit, WorkManager, services, receivers, notifications, FCM, offline sync |
| **C: Android UI and design** | Screens, navigation, tablet layouts, dental chart view, animations, PDFs, Espresso tests |

Working agreements: all three do clinic interviews in week 1; agree the OpenAPI contract early so B and C build against mock responses; small pull requests reviewed by someone from another role; every person has a named backup owner; each area keeps short runbooks and ADRs; Friday demo to each other.

---

## 19. Roadmap and timeline

| Phase | Weeks | Deliverable |
| --- | --- | --- |
| 1. Offline core | 1 to 3 | Login, patients, appointments, billing basics in Room; camera, SMS, call, menus, dialogs, animations (most of Part A and Part B labs) |
| 2. Backend and sync | 4 to 6 | Spring Boot with tenancy, JWT, roles; Retrofit integration; WorkManager sync; notifications and foreground reminder service |
| 3. SaaS depth | 7 to 10 | Conflict-free scheduling, dental chart canvas, treatment plans, billing ledger, PDFs, images, FCM reminders, map and WebView screens |
| 4. Hardening | 11 to 12 | Field encryption, audit log, biometric lock, backups, tests, performance on low-end phones |
| 5. Release | 13 to 14 | Signed build, Play internal testing, 2 or 3 beta clinics, demo and documentation |

Phase 3 completion is the mini-project submission point. Phases 4 and 5 are the production extension. V2 and V3 follow after beta gates: 99.5% crash-free sessions, p95 under 300 ms, restore drill passed and a pen test completed.

---

## 20. Risks and mitigations

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Cross-tenant data leak | Critical | RLS, tenant only from token, isolation test suite in CI, pen test |
| Data loss or failed restore | Critical | WAL archiving, cross-region backups, monthly restore drill |
| Scope creep before first paying clinic | High | Hold the MVP list in Section 4; new requests go to V2 |
| Chart is slow on low-end devices | High | Cached paths, no allocations in `onDraw`, test on 3 GB RAM device each sprint |
| Sync conflicts and bugs | High | Append-only clinical data, UUIDv7 client ids, server-wins for appointments, start with a few tables |
| SMS DLT approval delays | Medium | Start DLT registration in week 1; push notifications as fallback |
| Regulatory change (DPDP rules, retention) | Medium | Legal review, configurable retention, consent records from day one |
| Clinics slow to leave paper | High | Free import from Excel, 14-day trial, in-person setup for the first 10 clinics |
| Deadline pressure from coursework | High | Treat milestone M6 as the submission point; keep production work additive |
| Small-team burnout | Medium | Modular code, ADRs, runbooks, automated tests, managed services |

---

## 21. Open questions

- [ ] Final product name and domain (check trademark and Play Store availability).
- [ ] Faculty confirmation: Java required, or Kotlin allowed? Is a Spring Boot backend acceptable as the "web service"?
- [ ] Maps: Google Maps SDK (needs API key and billing account) or osmdroid?
- [ ] Launch geography: India only, or more countries?
- [ ] Pricing validated with clinic interviews.
- [ ] Hosting budget: AWS managed services or a VPS for the first 20 clinics.
- [ ] Is SQLCipher (encrypted Room) in scope for MVP or hardening phase?

---

## 22. Build guide for Claude Code

### 22.1 Repo layout

```
dentacloud/
  PRD.md
  CLAUDE.md
  backend/            Spring Boot 3, Java 21, Gradle
  android/            Android Studio project, Java 17, Gradle
  infra/              docker-compose.yml, Dockerfiles, deployment notes
  docs/               ADRs, runbooks, API contract (openapi.yaml), test reports
```

### 22.2 Paste this into `CLAUDE.md`

```markdown
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

## Commands
- Backend: `./gradlew :backend:test`, `./gradlew :backend:bootRun`
- Android: `./gradlew :android:app:assembleDebug`, `./gradlew :android:app:testDebugUnitTest`
- Local stack: `docker compose -f infra/docker-compose.yml up -d`
```

### 22.3 Milestones and acceptance criteria

| # | Milestone | Weeks | Acceptance criteria |
| --- | --- | --- | --- |
| **M0** | Foundations | 1 | Monorepo, Gradle builds for backend and android, Docker Compose with PostgreSQL and Redis, CI runs tests, `docs/openapi.yaml` skeleton, Android app launches with splash animation and Hilt wired |
| **M1** | Android offline core | 1 to 3 | Login screen (SharedPreferences session), patient CRUD in Room with search, appointment booking with date picker and spinner, billing calculator and procedure checkboxes with Toast total, options and context menus, SMS and call intents, camera capture, confirmation dialogs. Works with no backend |
| **M2** | Backend identity and tenancy | 4 | Clinic signup, login, refresh rotation, roles and scopes, RLS on core tables, tenant-isolation test suite passes, OpenAPI published |
| **M3** | Patients, scheduling and sync | 5 to 6 | Patient and appointment APIs, exclusion constraints, 100-thread double-booking test passes, `sync/pull` and `sync/push`, Android Retrofit client generated, `pending_op` queue and WorkManager sync, Sync status chip |
| **M4** | Clinical and billing | 7 to 8 | Visits, FDI dental chart Canvas view with history, treatment plans, invoices, append-only payment ledger with idempotency, receipt and prescription PDFs on device |
| **M5** | Reminders, maps, platform topics | 9 to 10 | Outbox worker with SMS and FCM, foreground reminder service, notification opens appointment detail, battery receiver banner, WebView screen, clinic map screen with navigation intent, lab-only AsyncTask demo screen |
| **M6** | Mini-project complete | 10 | Demo script runs end to end on a phone and a tablet; every syllabus experiment is demonstrable (Section 5); report and viva notes ready |
| **M7** | Security hardening | 11 to 12 | Field-level encryption with blind indexes, hash-chained audit log, biometric lock, `FLAG_SECURE`, encrypted backups with a tested restore, dependency scan clean |
| **M8** | Quality and performance | 12 to 13 | k6 load test meets targets, Espresso suite passes, cold start under 2 s on a 3 GB device, Crashlytics enabled, R8 build works |
| **M9** | Release | 13 to 14 | Signed `.aab`, Play internal testing, deployed staging and production, monitoring and alerts live, beta clinics onboarded |

### 22.4 Starter prompts

1. **M0:** "Read PRD.md and CLAUDE.md. Create the monorepo from section 22.1: a Spring Boot 3 / Java 21 Gradle module in `backend/`, an Android Java 17 app in `android/` with Hilt, Navigation and a splash animation, and `infra/docker-compose.yml` with PostgreSQL 16 and Redis. Add a GitHub Actions workflow that builds and tests both. Report what you created and how to run it."
2. **M1:** "Implement the Android offline core from milestone M1. Start with Room entities and DAOs for patient and appointment, then the patient list with search and a two-step registration form. Follow MVVM. Add unit tests for the DAOs and ViewModels. Do not call any backend yet."
3. **M2:** "Implement milestone M2 in `backend/`: Flyway migrations for clinic, app_user, role, role_permission, clinic_member and refresh_token from PRD section 10, JWT login with rotating refresh tokens, a tenant filter that sets `app.clinic_id`, RLS policies, and an integration test suite proving clinic A cannot access clinic B data."
4. **M3:** "Implement the appointment module with the two exclusion constraints from the PRD schema, the REST endpoints, and a Testcontainers test that fires 100 concurrent bookings for the same dentist and slot and asserts exactly one succeeds. Then implement `/sync/pull` and `/sync/push`."
5. **M4 (chart):** "Build a custom `ToothChartView` for the Android app that draws the FDI odontogram on a Canvas, supports tap to select tooth and surface, pinch zoom and pan, colours by condition, and avoids allocations in `onDraw`. Add a Robolectric or instrumented test for hit-testing."

### 22.5 Definition of done (every task)

- Code compiles; unit and integration tests pass.
- OpenAPI updated and the generated Android client still builds.
- No PHI in logs; permissions enforced on the server.
- Works offline where the PRD says so, and on a low-end device.
- Migration scripts included; documentation or ADR updated for any decision change.

### 22.6 What not to build yet

iOS app, public web app, patient portal, WhatsApp integration, multi-branch, AI features, public API. They are listed in V2 and V3 and stay out of the MVP.
