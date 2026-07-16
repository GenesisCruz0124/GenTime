-- GenTime — seed data for local development
-- Creates: 2 sites, 1 admin, 1 supervisor, 3 employees (+ shifts).
-- All demo accounts use the password:  Password123!
--
-- Note: inserting into auth.users fires handle_new_user(), which creates the
-- matching profiles row from raw_user_meta_data. We then patch site/supervisor.

-- ---------------------------------------------------------------------------
-- Sites
-- ---------------------------------------------------------------------------
insert into public.sites (id, name, lat, lng, radius_m) values
  ('11111111-1111-1111-1111-111111111111', 'HQ — Makati',      14.554700, 121.024500, 100),
  ('22222222-2222-2222-2222-222222222222', 'Warehouse — Pasig', 14.576500, 121.085300, 150)
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- Auth users (local dev). encrypted_password = crypt('Password123!', gen_salt('bf'))
-- ---------------------------------------------------------------------------
insert into auth.users
  (instance_id, id, aud, role, email, encrypted_password,
   email_confirmed_at, created_at, updated_at,
   raw_app_meta_data, raw_user_meta_data)
values
  ('00000000-0000-0000-0000-000000000000',
   'a0000000-0000-0000-0000-000000000001', 'authenticated', 'authenticated',
   'admin@gentime.dev', crypt('Password123!', gen_salt('bf')),
   now(), now(), now(),
   '{"provider":"email","providers":["email"]}',
   '{"employee_code":"GT-0001","full_name":"Ada Admin","role":"admin"}'),

  ('00000000-0000-0000-0000-000000000000',
   'a0000000-0000-0000-0000-000000000002', 'authenticated', 'authenticated',
   'sup@gentime.dev', crypt('Password123!', gen_salt('bf')),
   now(), now(), now(),
   '{"provider":"email","providers":["email"]}',
   '{"employee_code":"GT-0002","full_name":"Sam Supervisor","role":"supervisor"}'),

  ('00000000-0000-0000-0000-000000000000',
   'a0000000-0000-0000-0000-000000000003', 'authenticated', 'authenticated',
   'emp1@gentime.dev', crypt('Password123!', gen_salt('bf')),
   now(), now(), now(),
   '{"provider":"email","providers":["email"]}',
   '{"employee_code":"GT-0003","full_name":"Ela Employee","role":"employee"}'),

  ('00000000-0000-0000-0000-000000000000',
   'a0000000-0000-0000-0000-000000000004', 'authenticated', 'authenticated',
   'emp2@gentime.dev', crypt('Password123!', gen_salt('bf')),
   now(), now(), now(),
   '{"provider":"email","providers":["email"]}',
   '{"employee_code":"GT-0004","full_name":"Ben Employee","role":"employee"}'),

  ('00000000-0000-0000-0000-000000000000',
   'a0000000-0000-0000-0000-000000000005', 'authenticated', 'authenticated',
   'emp3@gentime.dev', crypt('Password123!', gen_salt('bf')),
   now(), now(), now(),
   '{"provider":"email","providers":["email"]}',
   '{"employee_code":"GT-0005","full_name":"Cora Employee","role":"employee"}')
on conflict (id) do nothing;

-- Identities (needed for email/password sign-in in local Supabase Auth)
insert into auth.identities
  (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
select u.id, u.id,
       jsonb_build_object('sub', u.id::text, 'email', u.email),
       'email', now(), now(), now()
from auth.users u
where u.email like '%@gentime.dev'
on conflict (provider, provider_id) do nothing;

-- ---------------------------------------------------------------------------
-- Patch profile assignments (roles/codes already set by the trigger)
-- ---------------------------------------------------------------------------
update public.profiles set site_id = '11111111-1111-1111-1111-111111111111'
  where employee_code = 'GT-0002';

update public.profiles
  set supervisor_id = 'a0000000-0000-0000-0000-000000000002',
      site_id = '11111111-1111-1111-1111-111111111111'
  where employee_code in ('GT-0003','GT-0004');

update public.profiles
  set supervisor_id = 'a0000000-0000-0000-0000-000000000002',
      site_id = '22222222-2222-2222-2222-222222222222'
  where employee_code = 'GT-0005';

-- ---------------------------------------------------------------------------
-- Shifts: Mon–Fri 09:00–18:00, 10 min grace (days: 2=Mon..6=Fri)
-- ---------------------------------------------------------------------------
insert into public.shifts (profile_id, days, start_time, end_time, grace_minutes)
select id, array[2,3,4,5,6]::smallint[], '09:00', '18:00', 10
from public.profiles
where employee_code in ('GT-0003','GT-0004','GT-0005')
on conflict do nothing;
