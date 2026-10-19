# DentaCloud demo script (MAD lab CS3604-1)

Every syllabus experiment (PRD §5) can be shown from this path. The **offline demo** login works without the backend.

## Setup
- **App only:** `./gradlew :android:app:installDebug` on an emulator or phone, then tap **Try the offline demo**.
- **With the backend:** `docker compose -f infra/docker-compose.yml up -d`, then `./gradlew :backend:bootRun`. In the app, tap **Create a clinic**. The emulator reaches the PC at `10.0.2.2:8080`; a real phone needs `-PapiBaseUrl=http://<pc-ip>:8080/`.

## Walkthrough
| # | Do this | Shows |
|---|---|---|
| 1 | Launch the app | B10: splash zoom, rotate and move animations |
| 2 | Log in, then More → Logout (confirm) → log in again | B9: SharedPreferences session · A9: confirmation dialog |
| 3 | Patients → New patient: step 1 (name, phone, date of birth, gender), then step 2 (photo, history, allergies) | B6: two-step form · A8: date picker and spinner · A10: camera |
| 4 | Open the patient: the photo is the header background; tap Call and SMS | A10 · A7: call · A6: SMS intent |
| 5 | Patients list: search by name, phone or `P-0001`, tap the call icon | B4: list from the database · B5: search then call |
| 6 | Long-press a patient → Change card color / Edit / Delete | A5: context menu · A3: random color |
| 7 | Overflow menu → Appointments / Patients | A4: options menu |
| 8 | Book: date, time, procedure spinner → toast | A8 |
| 9 | Calendar: day/week toggle; long-press a booking to recolor it | A3 · F4 |
| 10 | Patient → Bill: tick procedures → Show total; calculator +, −, ×, ÷, C; Issue invoice | A1: checkboxes with toast total · A2: calculator |
| 11 | Invoice: split payment (UPI, then cash), refund (confirm), Share receipt PDF | F8 ledger · PDF |
| 12 | Patient → Chart: tap tooth 36, surface O → Caries; pinch to zoom; drag the history slider | F5 · B10: blink on the selected tooth |
| 13 | Patient → Plan → Invoice selected; Visit; Prescribe → share PDF | F6, F7, F9 |
| 14 | Today → Chair-side reminders on, then book an appointment 10 minutes ahead | B1: foreground service · B7: notification opens the appointment |
| 15 | Emulator battery at 15%, unplugged | B3: low-battery banner |
| 16 | More → Lab: AsyncTask demo · Settings → Export patients (progress bar) | B2 |
| 17 | More → Dental care tips → About our clinic → Back | B8: WebView, two pages |
| 18 | More → Clinic location → Navigate | Objective 5: maps |
| 19 | Tablet emulator: navigation rail, patient list beside the profile | PRD 9: tablet layouts |
| 20 | Airplane mode → add a patient (chip shows "Offline (1)") → reconnect (chip shows "Synced") | F13: offline sync (backend mode) |
