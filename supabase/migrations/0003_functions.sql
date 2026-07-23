-- GenTime — schema
-- Migration 0003: server-side business logic (RPCs + scheduled jobs)

-- ---------------------------------------------------------------------------
-- Geo helper: great-circle distance in metres (Haversine)
-- ---------------------------------------------------------------------------
create or replace function public.distance_m(
  lat1 double precision, lng1 double precision,
  lat2 double precision, lng2 double precision
) returns double precision
language sql
immutable
as $$
  select 2 * 6371000 * asin(sqrt(
    power(sin(radians(lat2 - lat1) / 2), 2)
    + cos(radians(lat1)) * cos(radians(lat2))
      * power(sin(radians(lng2 - lng1) / 2), 2)
  ));
$$;

-- ---------------------------------------------------------------------------
-- submit_attendance_event (RPC)
-- SECURITY DEFINER: validated write path for the mobile app.
--   * enforces active profile + device binding
--   * idempotent on client_event_id (safe for offline retries)
--   * computes inside_geofence against the employee's site
--   * flags device/server time drift > 10 min
--   * raises a wrong_location_checkin alert when outside the geofence
-- Returns the (existing or new) attendance_events row as jsonb.
-- ---------------------------------------------------------------------------
create or replace function public.submit_attendance_event(
  p_client_event_id uuid,
  p_event_type text,
  p_event_at timestamptz,
  p_lat double precision,
  p_lng double precision,
  p_accuracy_m double precision,
  p_device_id text,
  p_is_offline_sync boolean default false
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_profile   public.profiles%rowtype;
  v_site      public.sites%rowtype;
  v_inside    boolean;
  v_drift     boolean;
  v_existing  public.attendance_events%rowtype;
  v_row       public.attendance_events%rowtype;
begin
  -- Idempotency: return the existing event unchanged on retry.
  select * into v_existing
  from public.attendance_events
  where client_event_id = p_client_event_id;
  if found then
    return to_jsonb(v_existing);
  end if;

  select * into v_profile from public.profiles where id = auth.uid();
  if not found then
    raise exception 'profile_not_found' using errcode = 'P0002';
  end if;
  if not v_profile.is_active then
    raise exception 'profile_inactive' using errcode = 'P0001';
  end if;

  -- Device binding: bind on first use, reject mismatches thereafter.
  if v_profile.device_id is null then
    update public.profiles set device_id = p_device_id where id = v_profile.id;
  elsif v_profile.device_id <> p_device_id then
    raise exception 'device_mismatch' using errcode = 'P0001';
  end if;

  if p_event_type not in ('check_in','check_out') then
    raise exception 'invalid_event_type' using errcode = 'P0001';
  end if;

  -- Geofence evaluation against the employee's assigned site.
  v_inside := null;
  if v_profile.site_id is not null and p_lat is not null and p_lng is not null then
    select * into v_site from public.sites where id = v_profile.site_id;
    if found then
      v_inside := public.distance_m(p_lat, p_lng, v_site.lat, v_site.lng) <= v_site.radius_m;
    end if;
  end if;

  -- Time integrity: device time vs. server receipt time.
  v_drift := abs(extract(epoch from (now() - p_event_at))) > 600;

  insert into public.attendance_events (
    client_event_id, profile_id, event_type, event_at,
    lat, lng, accuracy_m, inside_geofence, device_id,
    is_offline_sync, drift_flagged
  ) values (
    p_client_event_id, v_profile.id, p_event_type, p_event_at,
    p_lat, p_lng, p_accuracy_m, v_inside, p_device_id,
    coalesce(p_is_offline_sync, false), v_drift
  )
  returning * into v_row;

  -- Wrong-location check-in -> alert (FCM dispatched by the alerts trigger).
  if p_event_type = 'check_in' and v_inside is false then
    insert into public.alerts (profile_id, alert_type, details)
    values (
      v_profile.id, 'wrong_location_checkin',
      jsonb_build_object(
        'event_id', v_row.id, 'lat', p_lat, 'lng', p_lng,
        'accuracy_m', p_accuracy_m, 'event_at', p_event_at
      )
    );
  end if;

  return to_jsonb(v_row);
end;
$$;

grant execute on function public.submit_attendance_event(
  uuid, text, timestamptz, double precision, double precision,
  double precision, text, boolean
) to authenticated;

-- ---------------------------------------------------------------------------
-- compute_daily_records(p_date)
-- Pairs first check_in / last check_out per employee for the day, applies the
-- shift + grace to derive lateness and status, and honours approved leave.
-- Idempotent per (profile, day) via upsert. Runs nightly + on demand.
-- ---------------------------------------------------------------------------
create or replace function public.compute_daily_records(p_date date default (now() at time zone 'utc')::date)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer := 0;
  r record;
begin
  for r in
    select p.id as profile_id,
           s.start_time, s.end_time, s.grace_minutes, s.days
    from public.profiles p
    left join lateral (
      select * from public.shifts sh where sh.profile_id = p.id limit 1
    ) s on true
    where p.is_active
  loop
    declare
      v_first_in   timestamptz;
      v_last_out   timestamptz;
      v_minutes    integer;
      v_late       integer := 0;
      v_status     text;
      v_on_leave   boolean;
      v_scheduled  boolean;
      -- ISO-ish weekday mapped to spec's 1=Sun..7=Sat
      v_dow smallint := (extract(dow from p_date)::int + 1);
    begin
      select min(event_at) into v_first_in
      from public.attendance_events
      where profile_id = r.profile_id and event_type = 'check_in'
        and (event_at at time zone 'utc')::date = p_date;

      select max(event_at) into v_last_out
      from public.attendance_events
      where profile_id = r.profile_id and event_type = 'check_out'
        and (event_at at time zone 'utc')::date = p_date;

      select exists (
        select 1 from public.leave_requests lr
        where lr.profile_id = r.profile_id
          and lr.status = 'approved'
          and p_date between lr.date_from and lr.date_to
      ) into v_on_leave;

      v_scheduled := r.days is not null and v_dow = any(r.days);

      if v_on_leave then
        v_status := 'on_leave';
      elsif v_first_in is null then
        v_status := case when v_scheduled then 'absent' else 'pending' end;
      elsif v_last_out is null then
        v_status := 'incomplete';
      else
        v_minutes := greatest(0, (extract(epoch from (v_last_out - v_first_in)) / 60)::int);
        if r.start_time is not null then
          v_late := greatest(0,
            (extract(epoch from (
              (v_first_in at time zone 'utc')::time - r.start_time
            )) / 60)::int - coalesce(r.grace_minutes, 0)
          );
        end if;
        v_status := case when v_late > 0 then 'late' else 'present' end;
      end if;

      insert into public.daily_records (
        profile_id, work_date, first_in, last_out,
        minutes_worked, minutes_late, status, computed_at
      ) values (
        r.profile_id, p_date, v_first_in, v_last_out,
        v_minutes, v_late, v_status, now()
      )
      on conflict (profile_id, work_date) do update set
        first_in       = excluded.first_in,
        last_out       = excluded.last_out,
        minutes_worked = excluded.minutes_worked,
        minutes_late   = excluded.minutes_late,
        status         = excluded.status,
        computed_at    = excluded.computed_at
      -- never clobber an admin manual correction
      where public.daily_records.correction_note is null;

      v_count := v_count + 1;
    end;
  end loop;
  return v_count;
end;
$$;

-- ---------------------------------------------------------------------------
-- geofence_monitor
-- For each on-the-clock employee (last event is a check_in), compares their
-- latest ping to the site radius and raises an outside_geofence alert.
-- Debounced: only fires when there is no unacknowledged outside_geofence alert
-- already open for that employee.
-- ---------------------------------------------------------------------------
create or replace function public.geofence_monitor()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer := 0;
  r record;
begin
  for r in
    with on_clock as (
      select distinct on (ae.profile_id)
             ae.profile_id, ae.event_type
      from public.attendance_events ae
      order by ae.profile_id, ae.event_at desc
    )
    select p.id as profile_id, s.lat as site_lat, s.lng as site_lng, s.radius_m,
           lp.lat as ping_lat, lp.lng as ping_lng
    from on_clock oc
    join public.profiles p on p.id = oc.profile_id
    join public.sites s on s.id = p.site_id
    join lateral (
      select lat, lng from public.location_pings
      where profile_id = p.id order by pinged_at desc limit 1
    ) lp on true
    where oc.event_type = 'check_in'
  loop
    if public.distance_m(r.ping_lat, r.ping_lng, r.site_lat, r.site_lng) > r.radius_m then
      if not exists (
        select 1 from public.alerts
        where profile_id = r.profile_id
          and alert_type = 'outside_geofence'
          and acknowledged_at is null
      ) then
        insert into public.alerts (profile_id, alert_type, details)
        values (r.profile_id, 'outside_geofence',
                jsonb_build_object('lat', r.ping_lat, 'lng', r.ping_lng));
        v_count := v_count + 1;
      end if;
    end if;
  end loop;
  return v_count;
end;
$$;

-- ---------------------------------------------------------------------------
-- missed_checkin_monitor
-- For employees scheduled today whose shift start + grace has elapsed with no
-- check_in, raise a missed_checkin alert (once per day).
-- ---------------------------------------------------------------------------
create or replace function public.missed_checkin_monitor()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count integer := 0;
  v_today date := (now() at time zone 'utc')::date;
  v_dow smallint := (extract(dow from now() at time zone 'utc')::int + 1);
  r record;
begin
  for r in
    select p.id as profile_id, s.start_time, s.grace_minutes, s.days
    from public.profiles p
    join public.shifts s on s.profile_id = p.id
    where p.is_active and v_dow = any(s.days)
  loop
    if (now() at time zone 'utc')::time > (r.start_time + make_interval(mins => coalesce(r.grace_minutes,0)))
       and not exists (
         select 1 from public.attendance_events
         where profile_id = r.profile_id and event_type = 'check_in'
           and (event_at at time zone 'utc')::date = v_today
       )
       and not exists (
         select 1 from public.alerts
         where profile_id = r.profile_id and alert_type = 'missed_checkin'
           and (created_at at time zone 'utc')::date = v_today
       )
    then
      insert into public.alerts (profile_id, alert_type, details)
      values (r.profile_id, 'missed_checkin',
              jsonb_build_object('shift_start', r.start_time, 'date', v_today));
      v_count := v_count + 1;
    end if;
  end loop;
  return v_count;
end;
$$;

-- ---------------------------------------------------------------------------
-- purge_old_pings — retention: delete location_pings older than 7 days.
-- ---------------------------------------------------------------------------
create or replace function public.purge_old_pings()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare v_count integer;
begin
  delete from public.location_pings where pinged_at < now() - interval '7 days';
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

-- ---------------------------------------------------------------------------
-- New-user hook: when an auth user is created, ensure a profiles row exists.
-- employee_code / full_name come from user metadata set at provisioning time.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, employee_code, full_name, role)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'employee_code',
             'GT-' || substr(new.id::text, 1, 8)),
    coalesce(new.raw_user_meta_data->>'full_name', new.email),
    coalesce(new.raw_user_meta_data->>'role', 'employee')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
