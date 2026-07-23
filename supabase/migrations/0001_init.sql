-- GenTime — schema
-- Migration 0001: core tables
--
-- Note: `sites` is created before `profiles` because profiles.site_id references it.

-- ---------------------------------------------------------------------------
-- sites: assigned work locations (single-radius geofence per site)
-- ---------------------------------------------------------------------------
create table if not exists public.sites (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  lat double precision not null,
  lng double precision not null,
  radius_m integer not null default 100 check (radius_m > 0),
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- profiles: extends auth.users
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  employee_code text unique not null,          -- e.g. GT-0001
  full_name text not null,
  role text not null default 'employee'
    check (role in ('employee','supervisor','admin')),
  supervisor_id uuid references public.profiles(id) on delete set null,
  site_id uuid references public.sites(id) on delete set null,
  device_id text,                              -- bound device, null = not yet bound
  fcm_token text,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create index if not exists profiles_supervisor_id_idx on public.profiles(supervisor_id);
create index if not exists profiles_site_id_idx on public.profiles(site_id);

-- ---------------------------------------------------------------------------
-- shifts: simple schedule per employee (MVP: one shift template each)
-- ---------------------------------------------------------------------------
create table if not exists public.shifts (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  days smallint[] not null,                    -- 1=Sun ... 7=Sat
  start_time time not null,                    -- e.g. 09:00
  end_time time not null,                      -- e.g. 18:00
  grace_minutes integer not null default 10 check (grace_minutes >= 0),
  created_at timestamptz not null default now()
);

create index if not exists shifts_profile_id_idx on public.shifts(profile_id);

-- ---------------------------------------------------------------------------
-- attendance_events: raw check-in/out events (append-only)
-- ---------------------------------------------------------------------------
create table if not exists public.attendance_events (
  id uuid primary key default gen_random_uuid(),
  client_event_id uuid not null unique,        -- generated on device; idempotency key
  profile_id uuid not null references public.profiles(id) on delete cascade,
  event_type text not null check (event_type in ('check_in','check_out')),
  event_at timestamptz not null,               -- device timestamp of the tap
  lat double precision,
  lng double precision,
  accuracy_m double precision,
  inside_geofence boolean,
  device_id text not null,
  synced_at timestamptz not null default now(),
  is_offline_sync boolean not null default false,
  drift_flagged boolean not null default false -- device/server time drift > 10 min
);

create index if not exists attendance_events_profile_time_idx
  on public.attendance_events(profile_id, event_at desc);

-- ---------------------------------------------------------------------------
-- location_pings: live tracking while on-the-clock
-- ---------------------------------------------------------------------------
create table if not exists public.location_pings (
  id bigint generated always as identity primary key,
  profile_id uuid not null references public.profiles(id) on delete cascade,
  lat double precision not null,
  lng double precision not null,
  accuracy_m double precision,
  pinged_at timestamptz not null default now()
);

create index if not exists location_pings_profile_time_idx
  on public.location_pings(profile_id, pinged_at desc);

-- ---------------------------------------------------------------------------
-- daily_records: computed DTR (one row per employee per day)
-- ---------------------------------------------------------------------------
create table if not exists public.daily_records (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  work_date date not null,
  first_in timestamptz,
  last_out timestamptz,
  minutes_worked integer,
  minutes_late integer not null default 0,
  status text not null default 'pending'
    check (status in ('pending','present','late','absent','on_leave','incomplete')),
  correction_note text,                        -- set by admin manual correction
  corrected_by uuid references public.profiles(id) on delete set null,
  computed_at timestamptz,
  unique (profile_id, work_date)
);

create index if not exists daily_records_date_idx on public.daily_records(work_date);

-- ---------------------------------------------------------------------------
-- leave_requests
-- ---------------------------------------------------------------------------
create table if not exists public.leave_requests (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  leave_type text not null
    check (leave_type in ('vacation','sick','emergency','unpaid','other')),
  date_from date not null,
  date_to date not null,
  reason text,
  status text not null default 'pending'
    check (status in ('pending','approved','rejected','cancelled')),
  decided_by uuid references public.profiles(id) on delete set null,
  decided_at timestamptz,
  created_at timestamptz not null default now(),
  check (date_to >= date_from)
);

create index if not exists leave_requests_profile_idx on public.leave_requests(profile_id);
create index if not exists leave_requests_status_idx on public.leave_requests(status);

-- ---------------------------------------------------------------------------
-- alerts: geofence violations, missed check-ins, wrong-location check-ins
-- ---------------------------------------------------------------------------
create table if not exists public.alerts (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  alert_type text not null
    check (alert_type in ('outside_geofence','wrong_location_checkin','missed_checkin','missed_checkout')),
  details jsonb,
  acknowledged_by uuid references public.profiles(id) on delete set null,
  acknowledged_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists alerts_profile_idx on public.alerts(profile_id);
create index if not exists alerts_unacked_idx on public.alerts(acknowledged_at) where acknowledged_at is null;
