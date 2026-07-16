-- GenTime — schema
-- Migration 0002: helper functions + Row-Level Security
--
-- Access model (from the spec):
--   employee   -> own records only
--   supervisor -> own team (profiles whose supervisor_id = them) + web
--   admin      -> everything

-- ---------------------------------------------------------------------------
-- Helper functions (SECURITY DEFINER so they can read profiles without
-- tripping the very policies they support -> avoids recursive RLS).
-- ---------------------------------------------------------------------------

create or replace function public.current_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
  select role from public.profiles where id = auth.uid();
$$;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'admin'
  );
$$;

-- True when the current user is a supervisor who manages `target`,
-- or is the target themselves.
create or replace function public.manages(target uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select
    auth.uid() = target
    or exists (
      select 1 from public.profiles p
      where p.id = target and p.supervisor_id = auth.uid()
    );
$$;

-- ---------------------------------------------------------------------------
-- Enable RLS
-- ---------------------------------------------------------------------------
alter table public.sites             enable row level security;
alter table public.profiles          enable row level security;
alter table public.shifts            enable row level security;
alter table public.attendance_events enable row level security;
alter table public.location_pings    enable row level security;
alter table public.daily_records     enable row level security;
alter table public.leave_requests    enable row level security;
alter table public.alerts            enable row level security;

-- ---------------------------------------------------------------------------
-- profiles
--   employee: read own | supervisor: read team | admin: full
-- ---------------------------------------------------------------------------
create policy profiles_select on public.profiles
  for select using (
    id = auth.uid()
    or supervisor_id = auth.uid()
    or public.is_admin()
  );

create policy profiles_update_self_fcm on public.profiles
  for update using (id = auth.uid());

create policy profiles_admin_all on public.profiles
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- sites
--   employee: read assigned | supervisor: read all | admin: full
--   (reading all sites is harmless; writes are admin-only)
-- ---------------------------------------------------------------------------
create policy sites_select on public.sites
  for select using (true);

create policy sites_admin_write on public.sites
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- shifts
--   employee: read own | supervisor: read team | admin: full
-- ---------------------------------------------------------------------------
create policy shifts_select on public.shifts
  for select using (public.manages(profile_id) or public.is_admin());

create policy shifts_admin_write on public.shifts
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- attendance_events
--   employee: read own (insert via RPC only) | supervisor: read team | admin: full
--   Direct inserts are NOT permitted — the submit_attendance_event RPC
--   (SECURITY DEFINER) performs validated writes.
-- ---------------------------------------------------------------------------
create policy attendance_select on public.attendance_events
  for select using (public.manages(profile_id) or public.is_admin());

create policy attendance_admin_write on public.attendance_events
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- location_pings
--   employee: insert own | supervisor: read team | admin: full
-- ---------------------------------------------------------------------------
create policy pings_insert_own on public.location_pings
  for insert with check (profile_id = auth.uid());

create policy pings_select on public.location_pings
  for select using (public.manages(profile_id) or public.is_admin());

create policy pings_admin_write on public.location_pings
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- daily_records
--   employee: read own | supervisor: read team | admin: full (corrections)
-- ---------------------------------------------------------------------------
create policy daily_select on public.daily_records
  for select using (public.manages(profile_id) or public.is_admin());

create policy daily_admin_write on public.daily_records
  for all using (public.is_admin()) with check (public.is_admin());

-- ---------------------------------------------------------------------------
-- leave_requests
--   employee: insert/read own + cancel pending | supervisor: read team + approve/reject
--   admin: full
-- ---------------------------------------------------------------------------
create policy leave_select on public.leave_requests
  for select using (public.manages(profile_id) or public.is_admin());

create policy leave_insert_own on public.leave_requests
  for insert with check (profile_id = auth.uid());

-- Employee may cancel their own still-pending request; supervisor/admin may
-- decide (approve/reject) a team member's request.
create policy leave_update on public.leave_requests
  for update using (
    (profile_id = auth.uid())
    or (
      exists (select 1 from public.profiles p
              where p.id = leave_requests.profile_id and p.supervisor_id = auth.uid())
    )
    or public.is_admin()
  );

-- ---------------------------------------------------------------------------
-- alerts
--   employee: none | supervisor: read team + acknowledge | admin: full
-- ---------------------------------------------------------------------------
create policy alerts_select on public.alerts
  for select using (
    (exists (select 1 from public.profiles p
             where p.id = alerts.profile_id and p.supervisor_id = auth.uid()))
    or public.is_admin()
  );

create policy alerts_ack on public.alerts
  for update using (
    (exists (select 1 from public.profiles p
             where p.id = alerts.profile_id and p.supervisor_id = auth.uid()))
    or public.is_admin()
  );

create policy alerts_admin_write on public.alerts
  for all using (public.is_admin()) with check (public.is_admin());
