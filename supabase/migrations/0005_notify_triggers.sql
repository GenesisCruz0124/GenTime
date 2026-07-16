-- GenTime — schema
-- Migration 0005: FCM dispatch triggers
--
-- These triggers call the `notify` Edge Function via pg_net when notable rows
-- appear. They are guarded so migrations succeed where pg_net is unavailable
-- (e.g. bare local Postgres). Configuration is read from app settings that the
-- deploy step sets:
--   app.settings.functions_url         e.g. https://<ref>.functions.supabase.co
--   app.settings.service_role_key      service-role JWT (for the Authorization header)

create or replace function public.dispatch_notify(
  p_recipient uuid, p_title text, p_body text, p_data jsonb default '{}'::jsonb
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_url text := current_setting('app.settings.functions_url', true);
  v_key text := current_setting('app.settings.service_role_key', true);
begin
  if v_url is null or v_key is null then
    return; -- not configured; skip silently
  end if;
  if not exists (select 1 from pg_extension where extname = 'pg_net') then
    return;
  end if;

  perform net.http_post(
    url := v_url || '/notify',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || v_key
    ),
    body := jsonb_build_object(
      'recipient_profile_id', p_recipient,
      'title', p_title,
      'body', p_body,
      'data', p_data
    )
  );
end;
$$;

-- Alerts -> notify the affected employee's supervisor.
create or replace function public.on_alert_created()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_supervisor uuid;
  v_name text;
begin
  select supervisor_id, full_name into v_supervisor, v_name
  from public.profiles where id = new.profile_id;
  if v_supervisor is not null then
    perform public.dispatch_notify(
      v_supervisor,
      'GenTime alert: ' || new.alert_type,
      coalesce(v_name, 'An employee') || ' — ' || replace(new.alert_type, '_', ' '),
      jsonb_build_object('alert_id', new.id::text, 'type', new.alert_type)
    );
  end if;
  return new;
end;
$$;

drop trigger if exists trg_alert_notify on public.alerts;
create trigger trg_alert_notify
  after insert on public.alerts
  for each row execute function public.on_alert_created();

-- Leave requests -> notify supervisor on file; notify employee on decision.
create or replace function public.on_leave_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_supervisor uuid;
  v_name text;
begin
  select supervisor_id, full_name into v_supervisor, v_name
  from public.profiles where id = new.profile_id;

  if tg_op = 'INSERT' then
    if v_supervisor is not null then
      perform public.dispatch_notify(
        v_supervisor, 'Leave request filed',
        coalesce(v_name, 'An employee') || ' requested ' || new.leave_type || ' leave',
        jsonb_build_object('leave_id', new.id::text)
      );
    end if;
  elsif tg_op = 'UPDATE' and new.status is distinct from old.status
        and new.status in ('approved', 'rejected') then
    perform public.dispatch_notify(
      new.profile_id, 'Leave ' || new.status,
      'Your ' || new.leave_type || ' leave was ' || new.status,
      jsonb_build_object('leave_id', new.id::text)
    );
  end if;
  return new;
end;
$$;

drop trigger if exists trg_leave_notify on public.leave_requests;
create trigger trg_leave_notify
  after insert or update on public.leave_requests
  for each row execute function public.on_leave_change();
