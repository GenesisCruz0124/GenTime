-- GenTime — schema
-- Migration 0007: enable pg_net; fix dispatch_notify's config lookup
--
-- dispatch_notify (0005) read its functions URL and bearer key via
-- current_setting('app.settings.*', true), but nothing ever set those GUCs —
-- ALTER DATABASE ... SET requires superuser, which the migration role doesn't
-- have on managed Postgres. Every trigger dispatch was silently a no-op as a
-- result. pg_net itself was also never installed, so net.http_post() would
-- have failed outright once the settings were fixed.
--
-- Fix: install pg_net, and embed the functions URL + anon key directly in the
-- function instead of routing through current_setting(). Neither value is
-- secret — the URL is public, and the anon key is already shipped in the
-- mobile app and web env — so hardcoding them here is safe. The `notify`
-- Edge Function does its own privileged profiles lookup via its own
-- SUPABASE_SERVICE_ROLE_KEY env var; this bearer token only needs to satisfy
-- the Edge Function gateway's verify_jwt check, which a valid anon-role JWT
-- does.
create extension if not exists pg_net;

create or replace function public.dispatch_notify(
  p_recipient uuid, p_title text, p_body text, p_data jsonb default '{}'::jsonb
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_url text := 'https://waqqwxycsnaemzfwjkqm.supabase.co/functions/v1';
  v_key text := 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndhcXF3eHljc25hZW16Zndqa3FtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQzNTYyNTUsImV4cCI6MjA5OTkzMjI1NX0.3l9w3axIWq2UOkIUOGtR-aODgiCba5a7GUpNFrGwbSE';
begin
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
