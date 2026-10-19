# DentaCloud: viva notes

Short answers to the questions most likely to come up. Paths are under `android/app/src/main/java/com/dentacloud/app/` unless they start with `backend/`.

## The 30-second pitch

DentaCloud is a native Android app for dental clinics: patients, appointments, a dental chart, billing, prescriptions and reminders. It works completely offline on Room. When online, it syncs with a multi-tenant Spring Boot API. Every Part A and Part B experiment is a real feature, not a separate demo, except the AsyncTask lab screen. AsyncTask is deprecated, so production code uses ExecutorService.

## Android fundamentals

**Q: What architecture did you use, and why?**
MVVM. Fragments only draw and forward clicks. The ViewModel holds state as LiveData and survives rotation. The Repository is the single place that talks to Room or the network. This keeps logic testable without a device: `ViewModelTest` and `LedgerTest` run on the JVM.

**Q: Activity vs fragment? Why only one activity?**
`MainActivity` hosts a `NavHostFragment`. Each screen is a fragment destination in `nav_graph.xml`. A single activity means one toolbar, one back stack and easy tablet layouts (`layout-sw600dp`). The syllabus's "two activities" becomes two destinations with arguments in a `Bundle` (`ui/common/Args.java`).

**Q: Explain the activity lifecycle.**
onCreate → onStart (visible) → onResume (interactive) → onPause → onStop (hidden) → onDestroy. For example, `MainActivity` registers `BatteryReceiver` in `onStart` and unregisters it in `onStop`, so it costs nothing while the app is hidden. Fragments also have `onDestroyView`. The app nulls its ViewBinding there, because a fragment on the back stack outlives its view.

**Q: What is LiveData and why is it lifecycle-aware?**
An observable holder that only delivers updates to observers that are STARTED or RESUMED, and removes them on DESTROYED. Observing with `getViewLifecycleOwner()` means no leaks and no crashes from updating a dead view.

**Q: What is Hilt?**
Dependency injection. `di/AppModule` provides the Room database, DAOs and executor. `di/NetworkModule` provides OkHttp and Retrofit. ViewModels are `@HiltViewModel` and receive repositories through their constructors, so tests can pass fakes.

**Q: Why not do work on the main thread?**
The main thread draws the UI every ~16 ms, and blocking it for 5 s gives an ANR. Room even throws if you query on the main thread. Writes go through an `ExecutorService`, long work through WorkManager, and Room queries return LiveData, which runs on a background thread.

## Lab experiments: what to say for each

| Exp | One-line explanation |
|---|---|
| A1 Checkboxes + Toast | Each `CheckBox` adds its procedure's fee. `BillMath` sums integer paise and a Toast shows the total. |
| A2 Calculator | `util/Calculator.java` is a pure class (operand, operator, `C` clears), unit tested. Division by zero throws `ArithmeticException`, which `BillingFragment` catches and shows as an error. |
| A3 Random color | `Colors.randomLabel()` picks a random hue with fixed low saturation and high brightness (HSV), so every color is a readable pastel. It is saved on the patient row. |
| A4 Options menu | `MainActivity` inflates the overflow menu. Items navigate between Patients and Appointments. |
| A5 Context menu | `PatientAdapter` sets `setOnCreateContextMenuListener`. Edit navigates with an argument, Color recolors, Delete asks for confirmation first. |
| A6 SMS | `Intents.sms()` uses `ACTION_SENDTO` with `smsto:`, so the user's SMS app opens with the text filled in. No SEND_SMS permission is needed. |
| A7 Call | `ACTION_DIAL` with `tel:` opens the dialer. No CALL_PHONE permission is needed, and the user confirms the call. |
| A8 Date picker + spinner | `DatePickerDialog` for the date, a `Spinner` of procedures, then a Toast on booking. |
| A9 Confirmation dialog | `Dialogs.confirm()` wraps `MaterialAlertDialogBuilder` and runs the action only on the positive button. |
| A10 Camera | `ActivityResultContracts.TakePicture` writes to a `FileProvider` URI (`res/xml/file_paths.xml`). The photo becomes the profile header background. |
| B1 Service | `ReminderForegroundService` is a foreground service with a persistent notification (Android 8+ requires it for long-running work). |
| B2 AsyncTask | `AsyncTaskLabFragment`: `doInBackground` counts to 1000, `publishProgress` updates the `ProgressBar` on the UI thread. It holds a `WeakReference` to the fragment to avoid a leak. Real export uses an `ExecutorService`. |
| B3 Battery receiver | Listens for `ACTION_BATTERY_CHANGED` (a sticky broadcast). Low means ≤ 20 % and unplugged, which shows a banner. Registered at runtime, because manifest receivers can't get this broadcast. |
| B4 Insert/display DB | Room `@Insert`, then `@Query` returns `LiveData<List<PatientEntity>>`. The list updates by itself after an insert. |
| B5 Search then call | `PatientDao` matches name and phone with LIKE and the code (`P-0001`) exactly. Each row has a call icon (`ACTION_DIAL`). |
| B6 Two-screen form | Step 1 and step 2 share one `PatientFormViewModel` scoped to the activity (`new ViewModelProvider(requireActivity())`), so data survives going back and forth. |
| B7 Notification → screen | `NavDeepLinkBuilder` builds a `PendingIntent` to `dentacloud://appointment/{id}`. Tapping it opens the appointment detail with a correct back stack. |
| B8 WebView | Loads `assets/tips/index.html`, and a link opens `clinic.html`. Back goes back in WebView history first. |
| B9 SharedPreferences | `SessionStore` (save/read/clear on logout), `TokenStore`, `UiPrefs`. Writes use `apply()`, which is asynchronous. |
| B10 Animations | `res/anim/blink.xml`, `move.xml`, `rotate.xml`, `zoom.xml`. The splash plays them. The selected tooth blinks. The chart zooms with a pinch. |

## Data and sync

**Q: Room vs raw SQLite?**
Room checks SQL at compile time, maps rows to objects, returns LiveData, and has a migration framework. Underneath it is still SQLite.

**Q: How do you change the schema without losing data?**
Bump the version, add a `Migration` in `di/Migrations.java`, export the schema JSON (`android/app/schemas/`), and test it with `MigrationTest` (1 → 2).

**Q: How does offline work?**
Every local write also inserts a `pending_op` row in the same transaction. `SyncWorker` (WorkManager, network required, exponential backoff) pushes the ops in order, then pulls server changes since a stored cursor. If the server rejects an op as a conflict (say, the slot was taken on another device), the app marks it and shows it. The rest of the batch still applies.

**Q: Why WorkManager and not a Service for sync?**
WorkManager survives process death and reboot, respects Doze, and can wait for a network constraint. A service would be killed or blocked in the background.

## Backend (if asked)

**Q: How do you stop clinic A from seeing clinic B's data?**
Every table has `clinic_id`. PostgreSQL row-level security policies compare it with `app.clinic_id`, which `TenantFilter` sets from the JWT for every transaction. `FORCE ROW LEVEL SECURITY` means even the table owner is filtered. `TenantIsolationTest` proves it.

**Q: How do you prevent double-booking?**
In the database, not in Java:
```sql
EXCLUDE USING gist (dentist_id WITH =, tstzrange(start_at, end_at) WITH &&)
```
A second constraint does the same for chairs. `BookingTest` fires 100 concurrent bookings for one slot and exactly one wins. A check-then-insert in Java would race.

**Q: How does login stay secure?**
The access JWT lives 15 minutes. The refresh token is single-use: each refresh returns a new one. If an already-used refresh token shows up, it was stolen, so the whole token family is revoked (`TokenService`). On Android, `TokenAuthenticator` refreshes once on a 401 and retries.

**Q: Why is money a `long`?**
Floats can't represent 0.1 exactly, so rounding errors creep into bills. All amounts are integer paise and are converted to ₹ only for display (`util/Money.java`).

**Q: What does append-only mean for payments?**
Payment rows are never updated or deleted. A refund is a new negative entry, and the balance is the sum. Each payment carries an `Idempotency-Key`, so a retried request after a timeout does not charge twice.

**Q: What is the outbox?**
The reminder row is written in the same transaction as the appointment. `OutboxWorker` sends it later (SMS, data-only FCM) and retries on failure. No reminder is lost if the SMS provider is down, and none is sent for a booking that rolled back.

## Security and privacy

- No patient names, phones or notes appear in logs, SMS text or push text.
- The server checks permissions on every endpoint. Hiding a button is not security.
- Secrets live in environment variables (`.env.example`), never in the repo. The app uses dummy data only.

## Security hardening (M7)

**Q: How is patient data encrypted in the database?**
Sensitive fields use AES-256-GCM. Each clinic has its own data key, stored only in wrapped form: it is encrypted with a master key from the environment, which stands in for a cloud KMS. The clinic id goes in as GCM "additional authenticated data", so a value pasted into another clinic's row fails the integrity check instead of decrypting (`FieldCryptoTest`).

**Q: If the phone number is encrypted, how do you search by phone?**
With a blind index. The server stores HMAC-SHA256 of the last 10 digits, which is deterministic, so searching for "9845000001" matches "+91 98450 00001" without decrypting anything. Without the HMAC key, the index can't be reversed by guessing numbers.

**Q: Why a new key per clinic?**
It limits the damage. A leaked clinic key exposes one clinic, and a clinic's data can be crypto-shredded by destroying its key. A new key is committed in its own transaction before it is used, so a rolled-back request can never leave data encrypted under a key that was never saved.

**Q: What makes the audit log tamper-evident?**
Each row stores SHA-256(previous hash + this row's fields), so editing or deleting any row breaks every hash after it. `GET /audit-events/verify` re-walks the chain and returns the first broken row (`AuditTest` edits a row as the database superuser and verify catches it). The app's database role can't UPDATE or DELETE audit rows at all.

**Q: Do you audit reads too?**
Yes, every clinical GET: patient, chart, visits, plans, prescriptions and sync pulls. A `ResponseBodyAdvice` writes the audit row after the controller succeeds but before the response body is sent. If the audit write fails, the request fails, so no data goes out unlogged. Search terms are never logged, because a phone number is patient data.

**Q: What does the app do to protect data on the phone?**
Tokens are in Keystore-backed EncryptedSharedPreferences. `FLAG_SECURE` blocks screenshots and the recent-apps preview on patient screens. Device backup is disabled (`allowBackup=false`). There is an optional biometric lock (BiometricPrompt, fingerprint, face or device PIN), which locks on a cold start and after 60 s in the background.

**Q: How do you know backups actually work?**
A restore drill restores the backup into a throwaway Postgres, then compares every table's row count and the audit-chain digest with a manifest written at backup time. We also checked that it fails on a wrong passphrase, a tampered file and missing data. gpg's integrity check rejects a modified backup.

**Q: What is a dependency scan and what did it find?**
Trivy checks every locked library version against known vulnerabilities (CVEs). The first scan found 11 HIGH/CRITICAL issues. Nine were fixed by upgrading Jackson, Tomcat and the PostgreSQL driver. Two Spring MVC issues need Spring Boot 4, and they only affect XSLT views and Server-Sent Events, which we don't use, so they are documented in `.trivyignore`.

## Honest limitations

- The app does not receive FCM yet (that needs a `google-services.json`). The backend sends it, and the app syncs on a WorkManager schedule instead.
- The map uses osmdroid (OpenStreetMap), so no API key is needed. **Navigate** hands off to an installed maps app.
- Backups stay on the database host for now: no copy to a second region yet, and no WAL archiving, so up to 24 hours of writes could be lost.
- Load tests, the Espresso UI suite and a signed release build are milestones M8 and M9.

## Numbers worth remembering

- 20/20 lab experiments, 5/5 course objectives
- Room: 14 entities, version 2, 1 tested migration
- Flyway: 5 migrations (V1 to V5)
- Android unit tests: 35, all passing · Backend integration tests: 38, all passing
- Restore drill: 28/28 checks · Dependency scan: 0 open HIGH/CRITICAL
- Access token 15 min · sync every 15 min · low battery ≤ 20 %
