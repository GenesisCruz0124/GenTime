-- GenTime — schema
-- Migration 0004: payroll report RPC, Realtime publication, scheduled jobs

-- ---------------------------------------------------------------------------
-- report_summary(from, to[, site])
-- Payroll-ready per-employee rollup over a date range. RLS on daily_records
-- still applies, so supervisors see only their team and admins see everyone.
-- ---------------------------------------------------------------------------
create or replace function public.report_summary(
  p_from date,
  p_to date,
  p_site_id uuid default null
)
returns table (
  profile_id uuid,
  employee_code text,
  full_name text,
  days_present integer,
  days_late integer,
  total_minutes_late integer,
  days_absent integer,
  days_on_leave integer,
  total_hours numeric
)
language sql
stable
security invoker
set search_path = public
as $$
  select
    p.id,
    p.employee_code,
    p.full_name,
    count(*) filter (where dr.status in ('present','late'))::int as days_present,
    count(*) filter (where dr.status = 'late')::int              as days_late,
    coalesce(sum(dr.minutes_late), 0)::int                        as total_minutes_late,
    count(*) filter (where dr.status = 'absent')::int             as days_absent,
    count(*) filter (where dr.status = 'on_leave')::int           as days_on_leave,
    round(coalesce(sum(dr.minutes_worked), 0) / 60.0, 2)          as total_hours
  from public.profiles p
  left join public.daily_records dr
    on dr.profile_id = p.id
   and dr.work_date between p_from and p_to
  where (p_site_id is null or p.site_id = p_site_id)
  group by p.id, p.employee_code, p.full_name
  order by p.employee_code;
$$;

grant execute on function public.report_summary(date, date, uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- Realtime: the web Live Map subscribes to new location_pings.
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (
    select 1 from pg_publication where pubname = 'supabase_realtime'
  ) then
    create publication supabase_realtime;
  end if;
end$$;

alter publication supabase_realtime add table public.location_pings;
alter publication supabase_realtime add table public.alerts;

-- ---------------------------------------------------------------------------
-- Scheduled jobs via pg_cron (times are UTC).
-- Wrapped in a guard so migrations still succeed where pg_cron is absent.
-- ---------------------------------------------------------------------------
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
    create extension if not exists pg_cron;

    -- Nightly DTR computation for "yesterday" (00:30 UTC).
    perform cron.schedule(
      'compute_daily_records_nightly', '30 0 * * *',
      $cron$ select public.compute_daily_records((now() at time zone 'utc')::date - 1); $cron$
    );

    -- Geofence check every 5 minutes.
    perform cron.schedule(
      'geofence_monitor_5min', '*/5 * * * *',
      $cron$ select public.geofence_monitor(); $cron$
    );

    -- Missed check-in sweep every 10 minutes.
    perform cron.schedule(
      'missed_checkin_10min', '*/10 * * * *',
      $cron$ select public.missed_checkin_monitor(); $cron$
    );

    -- Ping retention purge, daily at 01:00 UTC.
    perform cron.schedule(
      'purge_old_pings_daily', '0 1 * * *',
      $cron$ select public.purge_old_pings(); $cron$
    );
  end if;
end$$;
