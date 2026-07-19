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

- Migrations `0001`–`0006` (schema, RLS, RPCs, reports, Realtime, cron, FCM
  triggers, function-grant hardening).
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

## Verified against the live project

- Auth sign-in (email/password) for all roles.
- RLS isolation over real JWTs: employee sees only self; supervisor sees the
  team of 4; admin sees all 5.
- `submit_attendance_event` RPC computes geofence correctly (`inside = true` at
  HQ coordinates).
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

- **FCM / Firebase** — push is inert until a real Firebase project + credentials
  replace the placeholder `mobile/app/google-services.json`, `FCM_PROJECT_ID` /
  `FCM_ACCESS_TOKEN` are set on the `notify` function, and pg_net +
  `app.settings.functions_url` / `app.settings.service_role_key` are configured.
- **Signed release** — keystore + GitHub secrets to tag `v1.0.0`.

## Test build

A debug APK wired to this project is produced from `mobile/` with
`./gradlew :app:assembleDebug` (config comes from `mobile/local.properties`).
