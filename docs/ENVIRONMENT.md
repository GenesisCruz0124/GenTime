# GenTime — Live Environment

The MVP backend is provisioned on Supabase and verified end-to-end.

## Project

| | |
|---|---|
| Project | `gentime-mvp` |
| Ref | `waqqwxycsnaemzfwjkqm` |
| API URL | `https://waqqwxycsnaemzfwjkqm.supabase.co` |
| Region | `ap-southeast-1` (Singapore) |
| Anon key | public client key — see [`web/.env.example`](../web/.env.example) |

The anon key is a public client key (shipped in the app); access is enforced by
Row-Level Security, not by key secrecy. The **service-role** key is never
committed and must only live in server-side secrets.

## What's applied

- Migrations `0001`–`0007` (schema, RLS, RPCs, reports, Realtime, cron, FCM
  triggers, function-grant hardening, pg_net + notify dispatch fix).
- Seed data: 2 sites, 5 accounts, shifts.
- pg_cron jobs scheduled (nightly DTR, geofence 5-min, missed check-in 10-min,
  ping purge daily).
- Realtime enabled on `location_pings` and `alerts`.

## Demo accounts

All use password **`Password123!`**.

| Email | Role | Employee | Notes |
|---|---|---|---|
| `admin@gentime.dev` | admin | GT-0001 | full web access |
| `sup@gentime.dev` | supervisor | GT-0002 | team: GT-0003/4/5 |
| `emp1@gentime.dev` | employee | GT-0003 | HQ — Makati |
| `emp2@gentime.dev` | employee | GT-0004 | HQ — Makati |
| `emp3@gentime.dev` | employee | GT-0005 | Warehouse — Pasig |
| `emp4@gentime.dev` | employee | GT-0006 | created live via `provision_employee` (Dan Rivera) |

## Verified against the live project

- Auth sign-in (email/password) for all roles.
- RLS isolation over real JWTs: employee sees only self; supervisor sees the
  team of 4; admin sees all 5.
- `submit_attendance_event` RPC computes geofence correctly (`inside = true` at
  HQ coordinates).
- **FCM push — fully live and verified end-to-end** (not just deployed):
  real Firebase project (`gentime-9327a`), `google-services.json` in the app,
  `FCM_PROJECT_ID` / `FCM_SERVICE_ACCOUNT_JSON` set on the `notify` function,
  `pg_net` installed, and `dispatch_notify` wired to call it. Confirmed live:
  a device registered a real FCM token via `onNewToken`, a leave-request
  approval fired the `trg_leave_notify` trigger, `net.http_post` reached
  `notify`, `notify` minted an access token from the service account, and a
  push notification was delivered to and displayed on the physical device.
- Security advisors: no ERRORs; RLS enabled on every table. SECURITY DEFINER
  functions not meant for REST were locked down in migration `0006`.

## Run the web dashboard against it

```bash
cd web
cp .env.example .env
npm install
npm run dev        # http://localhost:5173 — sign in with an account above
```

## Edge Functions (deployed & verified)

| Function | verify_jwt | Verified live |
|---|---|---|
| `provision_employee` | yes | admin creates account → 201; employee → `forbidden` (403) |
| `export_report` | yes | returns payroll CSV for a date range (RLS-scoped) |
| `notify` | yes | resolves fcm_token; no-ops as `fcm_not_configured` until FCM env set |

`submit_attendance_event` is not deployed as a function — the mobile app calls
the in-database RPC of the same name directly.

## Not yet configured (post-Phase-1)

- **Signed distribution key** — the release keystore is the demo one committed
  in-repo (see below); fine for testing, not for real distribution.

## Test build

A debug APK wired to this project is produced from `mobile/` with
`./gradlew :app:assembleDebug` (config comes from `mobile/local.properties`).

## Signed release build

A **demo** keystore is committed at `mobile/keystore/gentime-demo.keystore`
(alias `gentime`, store/key password `gentime-demo-2026`) so releases sign
consistently and update-installs work across builds:

```bash
cd mobile
KEYSTORE_PATH=$PWD/keystore/gentime-demo.keystore \
KEYSTORE_PASSWORD=gentime-demo-2026 \
KEY_ALIAS=gentime KEY_PASSWORD=gentime-demo-2026 \
./gradlew :app:assembleRelease   # → app/build/outputs/apk/release/app-release.apk
```

> ⚠️ Demo-grade only: the keystore and its password are in the repo, so anyone
> with repo access can sign as this app. Before any real distribution
> (Play Store or wide sideloading), generate a private keystore, keep it out of
> git, and wire it through GitHub Actions secrets (`KEYSTORE_PATH`,
> `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` are already read by the
> build). Release builds are minified by R8 (~4 MB vs ~27 MB debug); ProGuard
> rules for Supabase/Ktor/serialization/Room live in `app/proguard-rules.pro`.
