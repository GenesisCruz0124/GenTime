# GenTime — MVP Specification

**Version:** 1.0.0 (spec)
**Scope:** Android-only MVP (Phases 1–4). 201 File module and advanced reports deferred to post-MVP.

---

## 1. Overview

GenTime is an attendance and HR tool consisting of:

1. **Android app** (employees + supervisors) — biometric check-in/out, GPS capture, offline-first sync, leave filing, personal attendance history.
2. **Web admin dashboard** (HR/management) — live employee map, geofence alerts, DTR review, leave approvals, payroll-ready exports.
3. **Supabase backend** — Postgres + RLS, Auth, Realtime, Edge Functions.

### MVP Defaults (locked)
- Biometric: on-device `BiometricPrompt` (fingerprint/face). No server-side face matching.
- Geofence: single configurable radius per site, default 100 m.
- Platform: Android native (Kotlin / Jetpack Compose). No iOS in MVP.
- Payroll: CSV/XLSX export only. No external payroll integration.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Mobile | Kotlin, Jetpack Compose, Material 3 |
| Biometrics | AndroidX `BiometricPrompt` (BIOMETRIC_STRONG preferred, fallback WEAK) |
| Location | FusedLocationProviderClient; foreground service while on-the-clock |
| Local DB / offline queue | Room + WorkManager (network-constrained sync worker) |
| Push | Firebase Cloud Messaging (FCM) |
| Web | Vite, React 18, TypeScript, Tailwind CSS |
| Map | MapLibre GL JS + OpenFreeMap tiles |
| Backend | Supabase: Postgres, Auth, Realtime, Storage, Edge Functions (Deno) |
| CI/CD | GitHub Actions, tag-triggered (`v*`) build + release, APK artifact `GenTime-vX.X.X.apk` |

---

## 3. Roles & Access

| Role | App access | Web access | Notes |
|---|---|---|---|
| `employee` | ✅ | ❌ | Own records only |
| `supervisor` | ✅ | ✅ (limited) | Own team: map, alerts, leave approvals |
| `admin` (HR/management) | optional | ✅ full | All employees, sites, reports, exports |

Enforced via Supabase Auth + `profiles.role` + Row Level Security on every table.

**Device binding:** one active device per employee. `profiles.device_id` stores a per-install ID (generated UUID stored in EncryptedSharedPreferences). Check-in RPC rejects mismatched device_id. Admin can reset binding.

---

## 4. Implementation status

This repository implements Phases 1–4 across three surfaces:

- **`supabase/`** — schema (`migrations/0001_init.sql`), RLS (`0002_rls.sql`),
  business logic RPCs (`0003_functions.sql`), reports + realtime + cron
  (`0004_reports_realtime_cron.sql`), FCM triggers (`0005_notify_triggers.sql`),
  seed data (`seed.sql`), and Edge Functions under `functions/`.
- **`web/`** — Vite + React dashboard: Login, Live Map, Today, Attendance (DTR
  with admin correction), Leave Approvals, Alerts, Employees & Sites, Reports
  (CSV/XLSX export).
- **`mobile/`** — Kotlin/Compose app: login + device binding, punch screen
  (BiometricPrompt + GPS), Room offline queue + WorkManager sync, My
  Attendance, Leave, Supervisor tab, Settings, foreground tracking service, FCM.

See the root [`README.md`](../README.md) for setup instructions.

---

## 5. Server Logic (Edge Functions / RPCs)

1. **`submit_attendance_event`** (RPC) — validates active profile, device binding,
   idempotency on `client_event_id`, computes `inside_geofence`, raises
   `wrong_location_checkin` alert when outside.
2. **`compute_daily_records`** (scheduled nightly + on-demand) — pairs first/last
   punch, applies shift + grace → `minutes_late`/`status`, honours approved leave.
3. **`geofence_monitor`** (every 5 min) — latest ping vs radius → `outside_geofence`
   (debounced).
4. **`missed_checkin_monitor`** (scheduled) — shift start + grace elapsed, no
   check-in → `missed_checkin`.
5. **`purge_old_pings`** (daily) — delete `location_pings` > 7 days.
6. **`export_report`** (invoked) — CSV for a date range; XLSX built client-side.

---

## 6. Release Workflow

- Semantic versioning starting `v1.0.0`.
- APK naming: `GenTime-vX.X.X.apk`.
- GitHub Actions: tag push `v*` → signed release APK → GitHub Release + notes.
- Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
  `SUPABASE_URL`, `SUPABASE_ANON_KEY`.

---

## 7. Post-MVP Backlog

1. 201 File module (employee profile, documents in Supabase Storage, HR-only RLS, payroll linkage)
2. Shift start/end reminders
3. Advanced/custom report formats
4. Multi-shift schedules, overtime rules, night differential
5. iOS app (Expo/React Native) if needed
6. Server-side face verification (if on-device biometric proves insufficient)
