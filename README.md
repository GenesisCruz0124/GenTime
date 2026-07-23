# GenTime

Attendance & HR platform — biometric check-in/out, GPS geofencing, offline-first
sync, leave management, and payroll-ready exports.

This is the **MVP** (Android-only, Phases 1–4). See [`docs/SPEC.md`](docs/SPEC.md)
for the full specification.

## Monorepo layout

```
GenTime/
├── supabase/          # Postgres schema, RLS, seed data, Edge Functions (Deno)
│   ├── migrations/    # Ordered SQL migrations
│   ├── functions/     # Edge Functions & RPC-backing logic
│   ├── config.toml
│   └── seed.sql
├── web/               # HR/management dashboard — Vite + React 18 + TS + Tailwind
│   └── src/
├── mobile/            # Android app — Kotlin + Jetpack Compose + Material 3
│   └── app/
├── docs/              # Specification & architecture notes
└── .github/workflows/ # CI/CD — tag-triggered signed APK build + release
```

## Surfaces

| Surface | Stack | Who |
|---|---|---|
| **Mobile** | Kotlin, Jetpack Compose, Room, WorkManager, FusedLocation, BiometricPrompt, FCM | Employees & supervisors |
| **Web** | Vite, React 18, TypeScript, Tailwind, MapLibre GL | HR / management / supervisors |
| **Backend** | Supabase — Postgres + RLS, Auth, Realtime, Storage, Edge Functions (Deno) | — |

## Quick start

### Backend (Supabase)

```bash
# Requires the Supabase CLI (https://supabase.com/docs/guides/cli)
supabase start                      # local stack
supabase db reset                   # apply migrations + seed.sql
supabase functions serve            # run Edge Functions locally
```

Migrations live in `supabase/migrations/` and run in filename order. Seed data
(demo org, sites, employees) is in `supabase/seed.sql`.

### Web dashboard

```bash
cd web
cp .env.example .env                # set VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY
npm install
npm run dev
```

### Android app

```bash
cd mobile
# Set SUPABASE_URL / SUPABASE_ANON_KEY in mobile/local.properties or via env
./gradlew assembleDebug             # debug build
./gradlew assembleRelease           # signed release (needs keystore secrets)
```

## Roles

| Role | App | Web | Scope |
|---|---|---|---|
| `employee` | ✅ | ❌ | Own records |
| `supervisor` | ✅ | ✅ (team) | Own team |
| `admin` | optional | ✅ full | Everything |

All access is enforced by Supabase Auth + `profiles.role` + Row-Level Security
on every table.

## Releases

Push a `v*` tag to trigger the GitHub Actions release workflow: it assembles a
signed release APK named `GenTime-vX.X.X.apk`, creates a GitHub Release, and
attaches the APK. See [`.github/workflows/release.yml`](.github/workflows/release.yml).

Required GitHub Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
`KEY_PASSWORD`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
