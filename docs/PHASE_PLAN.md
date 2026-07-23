# GenTime — Phase Plan

A delivery plan from the current codebase to a shippable Android-only MVP.
Status is grounded in what exists in the repo today.

**Legend:** ✅ done · 🟡 partial / needs wiring · ⬜ not started

Each phase follows the spec's cadence: **plan-first approval → build →
`verify-implementation` pass → next phase**, with the `allow-once-approver`
gate active throughout.

---

## Status snapshot (today)

| Area | State | Notes |
|---|---|---|
| DB schema + RLS | ✅ | Verified end-to-end on Postgres 16 (isolation, geofence, DTR, reports) |
| Business-logic RPCs | ✅ | `submit_attendance_event`, `compute_daily_records`, monitors, `report_summary` |
| Edge Functions (Deno) | ✅ | RPC wrappers + `notify` + `provision_employee` |
| Seed data | ✅ | Demo org, sites, 5 accounts, shifts |
| Web dashboard | ✅ code / 🟢 live-ready | Builds clean; env points at the live project |
| Android app | ✅ code / ✅ builds | Compiles in CI + locally (22 MB APK); supabase-kt 3.0.3 |
| Live Supabase project | ✅ | `gentime-mvp` — migrations 0001–0006 + seed applied, RLS verified over real JWTs |
| CI (web + android debug) | ✅ | Both jobs green on the branch |
| Release workflow (signed APK) | 🟡 | Present; needs keystore + secrets |
| Firebase / FCM | ⬜ | `google-services.json` is a placeholder |
| Push notifications wiring | 🟡 | DB triggers + `notify` fn exist; need FCM creds + pg_net settings |

---

## Phase 0 — Environment & provisioning ✅ *(mostly done)*

Stand up the infrastructure the code assumes. See [`ENVIRONMENT.md`](ENVIRONMENT.md).

- ✅ Supabase project `gentime-mvp` created; URL + anon key captured.
- ✅ Migrations 0001–0006 + seed applied to the live project.
- ✅ `pg_cron` enabled and 4 jobs scheduled; Realtime enabled.
- ⬜ Enable `pg_net` + set `app.settings.functions_url` /
  `app.settings.service_role_key` (needed only for FCM dispatch).
- ⬜ Create the Firebase project; replace `mobile/app/google-services.json`;
  add `FCM_PROJECT_ID` / service-account credentials to function secrets.
- ⬜ Generate an upload keystore; add GitHub secrets: `KEYSTORE_BASE64`,
  `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `SUPABASE_URL`,
  `SUPABASE_ANON_KEY`.

**Exit criteria:** ✅ migrations applied to a live project; RLS verified over
real JWTs. Remaining: Firebase + keystore secrets for push and signed release.

---

## Phase 1 — Backend foundation ✅ *(live & verified)*

Spec deliverables: schema + RLS + seed, Auth, Edge Functions 1–2, web login +
Employees & Sites CRUD.

- ✅ Schema, RLS, seed, RPCs, Edge Functions, web CRUD — all in repo.
- ✅ Applied to the live project; cron jobs scheduled.
- ✅ RLS confirmed with **real JWTs** over the REST API (employee sees self,
  supervisor sees team of 4, admin sees all 5).
- ✅ `submit_attendance_event` geofence verified live.
- ⬜ Remaining: deploy Edge Functions (`export_report`, `provision_employee`,
  `notify`) — core RPC paths already work without them.

**Exit criteria:** ✅ met — admin/supervisor/employee sign in and see exactly
their RLS-scoped rows against the live database.

---

## Phase 2 — Mobile core 🟡 *(code complete — compile & wire)*

Spec deliverables: login + device binding, punch screen (BiometricPrompt +
GPS), Room offline queue + WorkManager sync, My Attendance.

- ✅ All screens + offline queue + sync worker + biometric + device binding
  written.
- ⬜ **Compile the Android module in CI** (first real build) and fix any
  supabase-kt 3.6.0 API deltas the sandbox couldn't catch.
- ⬜ On-device pass: biometric prompt, GPS fix, offline punch → queue →
  sync-on-reconnect, device-binding rejection.

**Exit criteria:** an employee checks in offline, regains network, and the
event lands exactly once (idempotent) with the correct geofence flag.

---

## Phase 3 — Live ops 🟡 *(code complete — wire push + monitors)*

Spec deliverables: foreground tracking service + pings, live map, geofence +
missed-check-in monitors, alerts + FCM.

- ✅ Foreground `TrackingService`, ping insert, live map (Realtime), monitors,
  alert triggers written.
- ⬜ Verify the 5-min ping cadence + battery behaviour on device.
- ⬜ End-to-end FCM: token capture → `notify` fn → supervisor device receives
  wrong-location / missed-check-in pushes.
- 🟡 Confirm `geofence_monitor` / `missed_checkin_monitor` cron runs in the
  live project (debounce already verified locally).

**Exit criteria:** an on-the-clock employee leaving the geofence produces a
map colour change **and** a push to their supervisor within one monitor cycle.

---

## Phase 4 — DTR, leave & reports 🟡 *(code complete — validate flows)*

Spec deliverables: `compute_daily_records`, leave filing/approvals (mobile +
web), Today board, reports + CSV/XLSX export.

- ✅ DTR computation (verified: 30-min-late / 505-min math, `on_leave`),
  leave flows both surfaces, Today board, reports + exports written.
- ⬜ Validate the nightly `compute_daily_records` schedule end-to-end.
- ⬜ Confirm leave decision → employee FCM; admin DTR correction audit note.

**Exit criteria:** a full day of punches produces a correct DTR row overnight;
a payroll CSV/XLSX export matches the DTR for the period.

---

## Phase 5 — Hardening & first release *(~1–2 days)*

- ⬜ `verify-implementation` pass across all four phases on the live stack.
- ⬜ Security review: RLS on every table, RPC `search_path`, service-role key
  never shipped to clients, `enable_signup=false` confirmed.
- ⬜ Supabase advisors (lint/security/perf) clean.
- ⬜ Tag `v1.0.0` → release workflow builds signed `GenTime-v1.0.0.apk` and
  publishes the GitHub Release.
- ⬜ Smoke test the released APK on a physical device.

**Exit criteria:** signed APK from CI installs and runs the full happy path
against the production Supabase project.

---

## Cross-cutting workstreams

- **Time integrity:** drift > 10 min already flagged server-side
  (`drift_flagged`); add an admin review surface if desired.
- **Retention:** `purge_old_pings` (7-day) verified; confirm the daily cron.
- **Observability:** wire Supabase logs / function logs; add a simple health
  check for the monitors.
- **Accessibility & i18n:** pass on the web dashboard (labels, focus states)
  and app (content descriptions).

---

## Known risks / open items

1. **Android not yet compiled** — supabase-kt 3.6.0 API surface (filter DSL,
   `rpc`, `update` builders) is the most likely source of first-build fixes.
   Mitigation: CI `assembleDebug` on the next push.
2. **Firebase placeholder** — FCM is inert until a real project + credentials
   replace the placeholder `google-services.json`.
3. **pg_cron / pg_net availability** — migrations guard for their absence;
   scheduled jobs and push dispatch require them enabled in the project.
4. **Map tiles** — OpenFreeMap is a public endpoint; confirm rate limits or
   self-host tiles for production.

---

## Suggested next step

Push a branch to trigger CI so the **Android `assembleDebug`** runs for the
first time — that single signal resolves the biggest open risk (Phase 2) and
tells us exactly what, if anything, needs adjusting before on-device testing.
