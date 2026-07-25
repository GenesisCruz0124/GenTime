-- GenTime — schema
-- Migration 0008: Employee 201 File + payroll integration
--
-- Adds the HR "201 file": richer employee master data (position, hire date,
-- employment type on profiles), a 1:1 employee_details table for sensitive PII
-- + PH government IDs + pay rate, an employee_documents table backed by a
-- private Storage bucket, and a configurable Philippine payroll engine
-- (SSS / PhilHealth / Pag-IBIG / BIR withholding) whose rates live in editable
-- tables rather than hardcoded in code.
--
-- Access model (per requirements): admin (management/HR) can read + write
-- everything; supervisors get READ-ONLY access to their own team's 201 files;
-- employees cannot access 201 files at all.

-- ===========================================================================
-- 1. profiles: non-sensitive HR master fields (visible to team + admin, and
--    surfaced in payroll summaries). Additive + nullable, so existing rows and
--    the mobile client (which decodes profiles with ignoreUnknownKeys) are
--    unaffected.
-- ===========================================================================
alter table public.profiles add column if not exists position text;
alter table public.profiles add column if not exists date_hired date;
alter table public.profiles add column if not exists employment_type text
  check (employment_type in
    ('regular','probationary','contractual','project_based','part_time'));

-- ===========================================================================
-- 2. employee_details: sensitive PII, PH government IDs, and compensation.
--    1:1 with profiles. Admin read/write; supervisors read their team.
-- ===========================================================================
create table if not exists public.employee_details (
  profile_id uuid primary key references public.profiles(id) on delete cascade,
  birth_date date,
  gender text,
  civil_status text,
  address text,
  phone text,
  personal_email text,
  emergency_contact_name text,
  emergency_contact_phone text,
  emergency_contact_relation text,
  -- PH statutory identifiers
  sss_no text,
  philhealth_no text,
  pagibig_no text,
  tin_no text,
  -- Compensation (drives payroll). Sensitive -> lives here, admin-only write.
  pay_rate numeric(12,2),
  rate_type text default 'monthly'
    check (rate_type in ('monthly','daily','hourly')),
  updated_at timestamptz not null default now(),
  updated_by uuid references public.profiles(id) on delete set null
);

-- keep updated_at fresh
create extension if not exists moddatetime schema extensions;
drop trigger if exists employee_details_moddatetime on public.employee_details;
create trigger employee_details_moddatetime
  before update on public.employee_details
  for each row execute procedure extensions.moddatetime(updated_at);

-- ===========================================================================
-- 3. employee_documents: metadata for files stored in the employee-docs bucket.
-- ===========================================================================
create table if not exists public.employee_documents (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles(id) on delete cascade,
  doc_type text not null default 'other'
    check (doc_type in
      ('resume','contract','govt_id','nbi_clearance','sss','philhealth',
       'pagibig','tin','birth_certificate','diploma','medical','other')),
  label text,
  storage_path text not null,   -- path within the 'employee-docs' bucket
  mime_type text,
  size_bytes bigint,
  uploaded_by uuid references public.profiles(id) on delete set null,
  uploaded_at timestamptz not null default now()
);
create index if not exists employee_documents_profile_idx
  on public.employee_documents(profile_id);

-- ===========================================================================
-- 4. RLS: admin read/write; supervisor read team (public.manages already
--    returns true for a supervisor over their reports, and for self).
-- ===========================================================================
alter table public.employee_details   enable row level security;
alter table public.employee_documents enable row level security;

-- employee_details
drop policy if exists employee_details_read on public.employee_details;
create policy employee_details_read on public.employee_details
  for select using (public.is_admin() or public.manages(profile_id));

drop policy if exists employee_details_admin_write on public.employee_details;
create policy employee_details_admin_write on public.employee_details
  for all using (public.is_admin()) with check (public.is_admin());

-- employee_documents
drop policy if exists employee_documents_read on public.employee_documents;
create policy employee_documents_read on public.employee_documents
  for select using (public.is_admin() or public.manages(profile_id));

drop policy if exists employee_documents_admin_write on public.employee_documents;
create policy employee_documents_admin_write on public.employee_documents
  for all using (public.is_admin()) with check (public.is_admin());

-- ===========================================================================
-- 5. Storage bucket + policies. Private bucket; path convention is
--    "{profile_id}/{uuid}-{filename}", so the first folder segment is the
--    employee's profile id. Admin read/write; supervisors download their team.
-- ===========================================================================
insert into storage.buckets (id, name, public)
values ('employee-docs', 'employee-docs', false)
on conflict (id) do nothing;

drop policy if exists "employee_docs_read" on storage.objects;
create policy "employee_docs_read" on storage.objects
  for select using (
    bucket_id = 'employee-docs' and (
      public.is_admin()
      or public.manages(((storage.foldername(name))[1])::uuid)
    )
  );

drop policy if exists "employee_docs_insert" on storage.objects;
create policy "employee_docs_insert" on storage.objects
  for insert with check (bucket_id = 'employee-docs' and public.is_admin());

drop policy if exists "employee_docs_update" on storage.objects;
create policy "employee_docs_update" on storage.objects
  for update using (bucket_id = 'employee-docs' and public.is_admin());

drop policy if exists "employee_docs_delete" on storage.objects;
create policy "employee_docs_delete" on storage.objects
  for delete using (bucket_id = 'employee-docs' and public.is_admin());

-- ===========================================================================
-- 6. Configurable PH payroll rate tables.
--    IMPORTANT: seeded values are RECENT-KNOWN defaults, NOT authoritative for
--    any given year. Statutory rates change by SSS/PhilHealth/Pag-IBIG/BIR
--    circular. HR MUST verify and update these before running real payroll.
--    Readable by any authenticated user (rates aren't PII); admin-only write.
-- ===========================================================================
create table if not exists public.payroll_settings (
  id boolean primary key default true check (id),   -- single-row guard
  -- SSS: employee share of the Monthly Salary Credit, clamped to [floor,ceil].
  sss_ee_rate numeric not null default 0.05,
  sss_msc_floor numeric not null default 5000,
  sss_msc_ceiling numeric not null default 35000,
  -- PhilHealth: premium rate on salary within [floor,ceil], split 50/50.
  philhealth_rate numeric not null default 0.05,
  philhealth_floor numeric not null default 10000,
  philhealth_ceiling numeric not null default 100000,
  -- Pag-IBIG: EE rate (low if monthly comp <= threshold, else high),
  -- computed on compensation capped at base_ceiling.
  pagibig_ee_rate_low numeric not null default 0.01,
  pagibig_ee_rate_high numeric not null default 0.02,
  pagibig_threshold numeric not null default 1500,
  pagibig_base_ceiling numeric not null default 5000,
  -- Working-time assumptions for prorating daily/hourly gross + absences.
  working_days_per_month numeric not null default 22,
  hours_per_day numeric not null default 8,
  rates_verified boolean not null default false,
  notes text default 'SEEDED DEFAULTS — verify against current BIR/SSS/PhilHealth/Pag-IBIG circulars before use.',
  updated_at timestamptz not null default now()
);
insert into public.payroll_settings (id) values (true) on conflict (id) do nothing;

-- BIR monthly withholding-tax brackets (TRAIN, post-2023). Graduated:
-- tax = base_tax + rate * (taxable_monthly - lower_bound).
create table if not exists public.payroll_tax_brackets (
  id serial primary key,
  lower_bound numeric not null,
  base_tax numeric not null,
  rate numeric not null
);
insert into public.payroll_tax_brackets (lower_bound, base_tax, rate)
select * from (values
  (0, 0, 0.00),
  (20833, 0, 0.15),
  (33333, 2500.00, 0.20),
  (66667, 10833.33, 0.25),
  (166667, 40833.33, 0.30),
  (666667, 190833.33, 0.35)
) as v(lower_bound, base_tax, rate)
where not exists (select 1 from public.payroll_tax_brackets);

alter table public.payroll_settings     enable row level security;
alter table public.payroll_tax_brackets enable row level security;

drop policy if exists payroll_settings_read on public.payroll_settings;
create policy payroll_settings_read on public.payroll_settings
  for select using (auth.uid() is not null);
drop policy if exists payroll_settings_admin_write on public.payroll_settings;
create policy payroll_settings_admin_write on public.payroll_settings
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists payroll_tax_read on public.payroll_tax_brackets;
create policy payroll_tax_read on public.payroll_tax_brackets
  for select using (auth.uid() is not null);
drop policy if exists payroll_tax_admin_write on public.payroll_tax_brackets;
create policy payroll_tax_admin_write on public.payroll_tax_brackets
  for all using (public.is_admin()) with check (public.is_admin());

-- ===========================================================================
-- 7. compute_payroll(profile, from, to): gross -> statutory deductions -> net,
--    prorated by attendance over the period. SECURITY INVOKER so RLS decides
--    who may compute for whom (admin: anyone; supervisor: their team; the read
--    of employee_details is what gates it).
-- ===========================================================================
create or replace function public.compute_payroll(
  p_profile_id uuid,
  p_from date,
  p_to date
)
returns table (
  profile_id uuid,
  employee_code text,
  full_name text,
  rate_type text,
  pay_rate numeric,
  days_present integer,
  days_absent integer,
  gross_pay numeric,
  absence_deduction numeric,
  sss_ee numeric,
  philhealth_ee numeric,
  pagibig_ee numeric,
  taxable_income numeric,
  withholding_tax numeric,
  total_deductions numeric,
  net_pay numeric
)
language plpgsql
stable
security invoker
set search_path = public
as $$
declare
  s public.payroll_settings;
  v_rate numeric;
  v_rate_type text;
  v_present int;
  v_absent int;
  v_hours numeric;
  v_daily numeric;
  v_gross numeric;
  v_absence_ded numeric;
  v_monthly_base numeric;   -- basis for statutory contributions
  v_sss numeric;
  v_phil numeric;
  v_pagibig numeric;
  v_taxable numeric;
  v_wht numeric;
  b public.payroll_tax_brackets;
begin
  select * into s from public.payroll_settings where id;

  -- Reading employee_details here is RLS-gated; a caller who can't see it
  -- gets no row and the function returns nothing.
  select ed.pay_rate, ed.rate_type into v_rate, v_rate_type
  from public.employee_details ed where ed.profile_id = p_profile_id;
  if v_rate is null then
    return;  -- no compensation on file
  end if;
  v_rate_type := coalesce(v_rate_type, 'monthly');

  select
    count(*) filter (where dr.status in ('present','late'))::int,
    count(*) filter (where dr.status = 'absent')::int,
    coalesce(sum(dr.minutes_worked), 0) / 60.0
  into v_present, v_absent, v_hours
  from public.daily_records dr
  where dr.profile_id = p_profile_id
    and dr.work_date between p_from and p_to;

  -- Gross for the period + the monthly base used for contribution formulas.
  if v_rate_type = 'monthly' then
    v_monthly_base := v_rate;
    v_daily := v_rate / nullif(s.working_days_per_month, 0);
    v_gross := v_rate;
    v_absence_ded := round(coalesce(v_daily, 0) * coalesce(v_absent, 0), 2);
  elsif v_rate_type = 'daily' then
    v_daily := v_rate;
    v_gross := round(v_rate * coalesce(v_present, 0), 2);
    v_monthly_base := round(v_rate * s.working_days_per_month, 2);
    v_absence_ded := 0;  -- daily rate: unworked days simply aren't paid
  else -- hourly
    v_gross := round(v_rate * coalesce(v_hours, 0), 2);
    v_monthly_base := round(v_rate * s.hours_per_day * s.working_days_per_month, 2);
    v_absence_ded := 0;
  end if;

  -- Statutory employee-share contributions, on the monthly base.
  v_sss := round(least(greatest(v_monthly_base, s.sss_msc_floor), s.sss_msc_ceiling)
                 * s.sss_ee_rate, 2);
  v_phil := round(least(greatest(v_monthly_base, s.philhealth_floor), s.philhealth_ceiling)
                  * s.philhealth_rate / 2.0, 2);
  v_pagibig := round(least(v_monthly_base, s.pagibig_base_ceiling)
                     * (case when v_monthly_base <= s.pagibig_threshold
                             then s.pagibig_ee_rate_low else s.pagibig_ee_rate_high end), 2);

  -- Taxable = gross less absences less mandatory contributions.
  v_taxable := greatest(v_gross - v_absence_ded - (v_sss + v_phil + v_pagibig), 0);

  select * into b from public.payroll_tax_brackets
  where lower_bound <= v_taxable order by lower_bound desc limit 1;
  v_wht := round(coalesce(b.base_tax, 0) + coalesce(b.rate, 0) * (v_taxable - coalesce(b.lower_bound, 0)), 2);

  return query
  select
    p.id, p.employee_code, p.full_name,
    v_rate_type, v_rate,
    coalesce(v_present, 0), coalesce(v_absent, 0),
    v_gross, v_absence_ded,
    v_sss, v_phil, v_pagibig,
    v_taxable, v_wht,
    round(v_absence_ded + v_sss + v_phil + v_pagibig + v_wht, 2) as total_deductions,
    round(v_gross - (v_absence_ded + v_sss + v_phil + v_pagibig + v_wht), 2) as net_pay
  from public.profiles p where p.id = p_profile_id;
end;
$$;

grant execute on function public.compute_payroll(uuid, date, date) to authenticated;

-- ===========================================================================
-- 8. Extend report_summary to carry 201-file master data (position, hire date,
--    employment type) so the payroll summary needs no separate encoding.
--    Return-column changes require drop + recreate.
-- ===========================================================================
drop function if exists public.report_summary(date, date, uuid);
create function public.report_summary(
  p_from date,
  p_to date,
  p_site_id uuid default null
)
returns table (
  profile_id uuid,
  employee_code text,
  full_name text,
  "position" text,
  date_hired date,
  employment_type text,
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
    p.position,
    p.date_hired,
    p.employment_type,
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
  group by p.id, p.employee_code, p.full_name, p.position, p.date_hired, p.employment_type
  order by p.employee_code;
$$;

grant execute on function public.report_summary(date, date, uuid) to authenticated;
