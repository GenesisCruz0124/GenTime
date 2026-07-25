import { useCallback, useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import { useAuth } from "../auth/AuthContext";
import type {
  Profile, EmployeeDetails, EmployeeDocument, PayrollResult,
  DocType, EmploymentType, RateType,
} from "../lib/types";

const iso = (d: Date) => d.toISOString().slice(0, 10);
const peso = (n: number) =>
  `₱${n.toLocaleString("en-PH", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const DOC_TYPES: DocType[] = [
  "resume", "contract", "govt_id", "nbi_clearance", "sss", "philhealth",
  "pagibig", "tin", "birth_certificate", "diploma", "medical", "other",
];
const EMPLOYMENT_TYPES: EmploymentType[] = [
  "regular", "probationary", "contractual", "project_based", "part_time",
];

export default function EmployeeFiles() {
  const { profile: me } = useAuth();
  const canEdit = me?.role === "admin";

  const [people, setPeople] = useState<Profile[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    supabase.from("profiles").select("*").order("employee_code").then(({ data }) => {
      const rows = (data as Profile[]) ?? [];
      setPeople(rows);
      setSelectedId((cur) => cur ?? rows[0]?.id ?? null);
    });
  }, []);

  return (
    <div>
      <h1 className="mb-1 text-2xl font-semibold">201 Files</h1>
      <p className="mb-4 text-sm text-slate-500">
        Employee master records, documents, and payroll.
        {canEdit ? " You can edit these files." : " Read-only for your team."}
      </p>
      <div className="grid gap-4 lg:grid-cols-[16rem_1fr]">
        <aside className="rounded-xl border border-slate-200 bg-white p-2">
          {people.map((p) => (
            <button key={p.id} onClick={() => setSelectedId(p.id)}
              className={`block w-full rounded-lg px-3 py-2 text-left text-sm ${
                selectedId === p.id ? "bg-brand text-white" : "hover:bg-slate-100"
              }`}>
              <div className="font-medium">{p.full_name}</div>
              <div className={`text-xs ${selectedId === p.id ? "text-white/80" : "text-slate-400"}`}>
                {p.employee_code}{p.position ? ` · ${p.position}` : ""}
              </div>
            </button>
          ))}
          {people.length === 0 && (
            <div className="p-3 text-sm text-slate-400">No employees visible.</div>
          )}
        </aside>

        {selectedId
          ? <EmployeeFile key={selectedId} profileId={selectedId} canEdit={canEdit} />
          : <div className="rounded-xl border border-slate-200 bg-white p-6 text-slate-400">Select an employee.</div>}
      </div>
    </div>
  );
}

function EmployeeFile({ profileId, canEdit }: { profileId: string; canEdit: boolean }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [details, setDetails] = useState<Partial<EmployeeDetails>>({});
  const [docs, setDocs] = useState<EmployeeDocument[]>([]);
  const [saving, setSaving] = useState(false);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    const [{ data: p }, { data: d }, { data: dl }] = await Promise.all([
      supabase.from("profiles").select("*").eq("id", profileId).single(),
      supabase.from("employee_details").select("*").eq("profile_id", profileId).maybeSingle(),
      supabase.from("employee_documents").select("*").eq("profile_id", profileId).order("uploaded_at", { ascending: false }),
    ]);
    setProfile(p as Profile);
    setDetails((d as EmployeeDetails) ?? { profile_id: profileId, rate_type: "monthly" });
    setDocs((dl as EmployeeDocument[]) ?? []);
  }, [profileId]);
  useEffect(() => { load(); }, [load]);

  if (!profile) return <div className="rounded-xl border border-slate-200 bg-white p-6 text-slate-400">Loading…</div>;

  const setP = (patch: Partial<Profile>) => setProfile({ ...profile, ...patch });
  const setD = (patch: Partial<EmployeeDetails>) => setDetails({ ...details, ...patch });

  const save = async () => {
    setSaving(true); setSavedMsg(null);
    await supabase.from("profiles").update({
      position: profile.position, date_hired: profile.date_hired || null,
      employment_type: profile.employment_type,
    }).eq("id", profileId);
    await supabase.from("employee_details").upsert({
      ...details,
      profile_id: profileId,
      pay_rate: details.pay_rate === undefined || details.pay_rate === null || (details.pay_rate as unknown) === ""
        ? null : Number(details.pay_rate),
    });
    setSaving(false); setSavedMsg("Saved."); load();
    setTimeout(() => setSavedMsg(null), 2500);
  };

  return (
    <div className="space-y-4">
      {/* header */}
      <section className="rounded-xl border border-slate-200 bg-white p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-xl font-semibold">{profile.full_name}</h2>
            <p className="text-sm text-slate-500">
              <span className="font-mono">{profile.employee_code}</span>
              {" · "}<span className="capitalize">{profile.role}</span>
              {profile.is_active ? "" : " · inactive"}
            </p>
          </div>
          {canEdit && (
            <div className="flex items-center gap-3">
              {savedMsg && <span className="text-sm text-green-600">{savedMsg}</span>}
              <button onClick={save} disabled={saving}
                className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark disabled:opacity-50">
                {saving ? "Saving…" : "Save changes"}
              </button>
            </div>
          )}
        </div>
      </section>

      <Section title="Employment">
        <Field label="Position" value={profile.position} disabled={!canEdit}
          onChange={(v) => setP({ position: v })} />
        <Field label="Date hired" type="date" value={profile.date_hired} disabled={!canEdit}
          onChange={(v) => setP({ date_hired: v })} />
        <SelectField label="Employment type" value={profile.employment_type ?? ""} disabled={!canEdit}
          options={EMPLOYMENT_TYPES} onChange={(v) => setP({ employment_type: (v || null) as EmploymentType | null })} />
      </Section>

      <Section title="Personal details">
        <Field label="Birth date" type="date" value={details.birth_date ?? null} disabled={!canEdit} onChange={(v) => setD({ birth_date: v })} />
        <Field label="Gender" value={details.gender ?? null} disabled={!canEdit} onChange={(v) => setD({ gender: v })} />
        <Field label="Civil status" value={details.civil_status ?? null} disabled={!canEdit} onChange={(v) => setD({ civil_status: v })} />
        <Field label="Phone" value={details.phone ?? null} disabled={!canEdit} onChange={(v) => setD({ phone: v })} />
        <Field label="Personal email" value={details.personal_email ?? null} disabled={!canEdit} onChange={(v) => setD({ personal_email: v })} />
        <Field label="Address" value={details.address ?? null} disabled={!canEdit} onChange={(v) => setD({ address: v })} wide />
      </Section>

      <Section title="Emergency contact">
        <Field label="Name" value={details.emergency_contact_name ?? null} disabled={!canEdit} onChange={(v) => setD({ emergency_contact_name: v })} />
        <Field label="Phone" value={details.emergency_contact_phone ?? null} disabled={!canEdit} onChange={(v) => setD({ emergency_contact_phone: v })} />
        <Field label="Relationship" value={details.emergency_contact_relation ?? null} disabled={!canEdit} onChange={(v) => setD({ emergency_contact_relation: v })} />
      </Section>

      <Section title="Government IDs">
        <Field label="SSS no." value={details.sss_no ?? null} disabled={!canEdit} onChange={(v) => setD({ sss_no: v })} />
        <Field label="PhilHealth no." value={details.philhealth_no ?? null} disabled={!canEdit} onChange={(v) => setD({ philhealth_no: v })} />
        <Field label="Pag-IBIG no." value={details.pagibig_no ?? null} disabled={!canEdit} onChange={(v) => setD({ pagibig_no: v })} />
        <Field label="TIN" value={details.tin_no ?? null} disabled={!canEdit} onChange={(v) => setD({ tin_no: v })} />
      </Section>

      <Section title="Compensation">
        <Field label="Pay rate (₱)" type="number" value={details.pay_rate != null ? String(details.pay_rate) : null}
          disabled={!canEdit} onChange={(v) => setD({ pay_rate: (v === "" ? null : Number(v)) as number | null })} />
        <SelectField label="Rate type" value={details.rate_type ?? "monthly"} disabled={!canEdit}
          options={["monthly", "daily", "hourly"]} onChange={(v) => setD({ rate_type: v as RateType })} />
      </Section>

      <Documents profileId={profileId} docs={docs} canEdit={canEdit} onChange={load} />
      <PayrollPanel profileId={profileId} />
    </div>
  );
}

function Documents({ profileId, docs, canEdit, onChange }: {
  profileId: string; docs: EmployeeDocument[]; canEdit: boolean; onChange: () => void;
}) {
  const { profile: me } = useAuth();
  const [docType, setDocType] = useState<DocType>("other");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const upload = async (file: File) => {
    setBusy(true); setErr(null);
    const path = `${profileId}/${crypto.randomUUID()}-${file.name}`;
    const { error: upErr } = await supabase.storage.from("employee-docs").upload(path, file);
    if (upErr) { setErr(upErr.message); setBusy(false); return; }
    const { error: insErr } = await supabase.from("employee_documents").insert({
      profile_id: profileId, doc_type: docType, label: file.name,
      storage_path: path, mime_type: file.type, size_bytes: file.size,
      uploaded_by: me?.id ?? null,
    });
    if (insErr) setErr(insErr.message);
    setBusy(false); onChange();
  };

  const download = async (d: EmployeeDocument) => {
    const { data, error } = await supabase.storage.from("employee-docs").createSignedUrl(d.storage_path, 60);
    if (error) { setErr(error.message); return; }
    window.open(data.signedUrl, "_blank");
  };

  const remove = async (d: EmployeeDocument) => {
    if (!confirm(`Delete "${d.label ?? d.storage_path}"?`)) return;
    await supabase.storage.from("employee-docs").remove([d.storage_path]);
    await supabase.from("employee_documents").delete().eq("id", d.id);
    onChange();
  };

  return (
    <Section title="Documents" plain>
      {canEdit && (
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <select value={docType} onChange={(e) => setDocType(e.target.value as DocType)}
            className="rounded-lg border border-slate-300 px-2 py-1.5 text-sm capitalize">
            {DOC_TYPES.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
          </select>
          <label className="cursor-pointer rounded-lg bg-brand px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-dark">
            {busy ? "Uploading…" : "Upload file"}
            <input type="file" className="hidden" disabled={busy}
              onChange={(e) => { const f = e.target.files?.[0]; if (f) upload(f); e.target.value = ""; }} />
          </label>
          {err && <span className="text-sm text-red-600">{err}</span>}
        </div>
      )}
      {docs.length === 0 ? (
        <p className="text-sm text-slate-400">No documents uploaded.</p>
      ) : (
        <table className="min-w-full text-sm">
          <thead className="text-left text-slate-500">
            <tr><th className="py-1 pr-4">Type</th><th className="py-1 pr-4">File</th><th className="py-1 pr-4">Uploaded</th><th></th></tr>
          </thead>
          <tbody>
            {docs.map((d) => (
              <tr key={d.id} className="border-t border-slate-100">
                <td className="py-1.5 pr-4 capitalize">{d.doc_type.replace(/_/g, " ")}</td>
                <td className="py-1.5 pr-4">
                  <button onClick={() => download(d)} className="text-brand hover:underline">{d.label ?? "file"}</button>
                </td>
                <td className="py-1.5 pr-4 text-slate-500">{d.uploaded_at.slice(0, 10)}</td>
                <td className="py-1.5">
                  {canEdit && <button onClick={() => remove(d)} className="text-xs text-red-600 hover:underline">delete</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Section>
  );
}

function PayrollPanel({ profileId }: { profileId: string }) {
  const [from, setFrom] = useState(iso(new Date(Date.now() - 30 * 864e5)));
  const [to, setTo] = useState(iso(new Date()));
  const [row, setRow] = useState<PayrollResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [ran, setRan] = useState(false);

  const run = async () => {
    setLoading(true); setRan(true);
    const { data } = await supabase.rpc("compute_payroll", {
      p_profile_id: profileId, p_from: from, p_to: to,
    });
    setRow(((data as PayrollResult[] | null) ?? [])[0] ?? null);
    setLoading(false);
  };

  return (
    <Section title="Payroll" plain>
      <p className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-700">
        Statutory rates (SSS / PhilHealth / Pag-IBIG / BIR) are configurable and
        seeded with recent defaults. Verify them in Payroll Settings before
        relying on these figures.
      </p>
      <div className="mb-3 flex flex-wrap items-end gap-2">
        <label className="text-sm"><span className="mb-1 block text-slate-500">From</span>
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="rounded-lg border border-slate-300 px-2 py-1" /></label>
        <label className="text-sm"><span className="mb-1 block text-slate-500">To</span>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="rounded-lg border border-slate-300 px-2 py-1" /></label>
        <button onClick={run} className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark">
          Compute payroll
        </button>
      </div>
      {loading ? <p className="text-sm text-slate-400">Computing…</p>
        : !ran ? <p className="text-sm text-slate-400">Choose a period and compute.</p>
        : !row ? <p className="text-sm text-slate-400">No pay rate on file for this employee.</p>
        : (
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="rounded-lg border border-slate-200 p-3">
              <div className="mb-2 text-xs font-semibold uppercase text-slate-400">Earnings</div>
              <Line label={`Gross (${row.rate_type})`} v={row.gross_pay} />
              <Line label={`Absences (${row.days_absent} d)`} v={-row.absence_deduction} />
            </div>
            <div className="rounded-lg border border-slate-200 p-3">
              <div className="mb-2 text-xs font-semibold uppercase text-slate-400">Deductions</div>
              <Line label="SSS" v={-row.sss_ee} />
              <Line label="PhilHealth" v={-row.philhealth_ee} />
              <Line label="Pag-IBIG" v={-row.pagibig_ee} />
              <Line label="Withholding tax" v={-row.withholding_tax} />
            </div>
            <div className="rounded-lg border border-slate-200 p-3 sm:col-span-2">
              <Line label="Taxable income" v={row.taxable_income} muted />
              <Line label="Total deductions" v={-row.total_deductions} />
              <div className="mt-1 border-t border-slate-200 pt-1">
                <Line label="Net pay" v={row.net_pay} bold />
              </div>
            </div>
          </div>
        )}
    </Section>
  );
}

function Line({ label, v, bold, muted }: { label: string; v: number; bold?: boolean; muted?: boolean }) {
  return (
    <div className={`flex justify-between py-0.5 text-sm ${bold ? "font-semibold" : ""} ${muted ? "text-slate-400" : ""}`}>
      <span>{label}</span>
      <span className={`font-mono ${v < 0 ? "text-red-600" : ""}`}>{peso(v)}</span>
    </div>
  );
}

function Section({ title, children, plain }: { title: string; children: React.ReactNode; plain?: boolean }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5">
      <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">{title}</h3>
      {plain ? children : <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{children}</div>}
    </section>
  );
}

function Field({ label, value, onChange, disabled, type = "text", wide }: {
  label: string; value: string | null; onChange: (v: string) => void;
  disabled?: boolean; type?: string; wide?: boolean;
}) {
  return (
    <label className={`text-sm ${wide ? "sm:col-span-2 lg:col-span-3" : ""}`}>
      <span className="mb-1 block text-slate-500">{label}</span>
      <input type={type} value={value ?? ""} disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-lg border border-slate-300 px-3 py-2 disabled:bg-slate-50 disabled:text-slate-500" />
    </label>
  );
}

function SelectField({ label, value, options, onChange, disabled }: {
  label: string; value: string; options: string[]; onChange: (v: string) => void; disabled?: boolean;
}) {
  return (
    <label className="text-sm">
      <span className="mb-1 block text-slate-500">{label}</span>
      <select value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-lg border border-slate-300 px-3 py-2 capitalize disabled:bg-slate-50 disabled:text-slate-500">
        <option value="">—</option>
        {options.map((o) => <option key={o} value={o}>{o.replace(/_/g, " ")}</option>)}
      </select>
    </label>
  );
}
