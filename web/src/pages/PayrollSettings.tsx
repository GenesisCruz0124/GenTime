import { useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import type { PayrollSettings as Settings } from "../lib/types";

interface TaxBracket { id: number; lower_bound: number; base_tax: number; rate: number; }

// Numeric settings fields grouped for a compact editor.
const GROUPS: { title: string; fields: [keyof Settings, string][] }[] = [
  { title: "SSS", fields: [
    ["sss_ee_rate", "Employee rate (e.g. 0.05)"],
    ["sss_msc_floor", "MSC floor"],
    ["sss_msc_ceiling", "MSC ceiling"],
  ] },
  { title: "PhilHealth", fields: [
    ["philhealth_rate", "Premium rate (e.g. 0.05)"],
    ["philhealth_floor", "Salary floor"],
    ["philhealth_ceiling", "Salary ceiling"],
  ] },
  { title: "Pag-IBIG", fields: [
    ["pagibig_ee_rate_low", "EE rate ≤ threshold"],
    ["pagibig_ee_rate_high", "EE rate > threshold"],
    ["pagibig_threshold", "Threshold"],
    ["pagibig_base_ceiling", "Base ceiling"],
  ] },
  { title: "Working time", fields: [
    ["working_days_per_month", "Working days / month"],
    ["hours_per_day", "Hours / day"],
  ] },
];

export default function PayrollSettings() {
  const [s, setS] = useState<Settings | null>(null);
  const [brackets, setBrackets] = useState<TaxBracket[]>([]);
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const load = () => {
    supabase.from("payroll_settings").select("*").eq("id", true).single()
      .then(({ data }) => setS(data as Settings));
    supabase.from("payroll_tax_brackets").select("*").order("lower_bound")
      .then(({ data }) => setBrackets((data as TaxBracket[]) ?? []));
  };
  useEffect(load, []);

  if (!s) return <div className="text-slate-400">Loading…</div>;

  const set = (k: keyof Settings, v: string) => setS({ ...s, [k]: Number(v) } as Settings);

  const save = async (verified?: boolean) => {
    setSaving(true); setMsg(null);
    const patch: Partial<Settings> = { ...s, updated_at: undefined as never };
    delete (patch as Record<string, unknown>).updated_at;
    if (verified !== undefined) patch.rates_verified = verified;
    const { error } = await supabase.from("payroll_settings").update(patch).eq("id", true);
    setSaving(false);
    setMsg(error ? error.message : "Saved.");
    if (!error) load();
    setTimeout(() => setMsg(null), 2500);
  };

  return (
    <div className="max-w-3xl">
      <h1 className="mb-1 text-2xl font-semibold">Payroll Settings</h1>
      <p className="mb-4 text-sm text-slate-500">
        Statutory contribution rates and payroll assumptions. These drive every
        payroll computation.
      </p>

      {!s.rates_verified && (
        <div className="mb-4 rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <strong>Unverified rates.</strong> The values below are seeded defaults,
          not authoritative for any specific year. Confirm them against the
          current SSS, PhilHealth, Pag-IBIG, and BIR circulars, then mark them
          verified.
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        {GROUPS.map((g) => (
          <section key={g.title} className="rounded-xl border border-slate-200 bg-white p-4">
            <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">{g.title}</h3>
            <div className="space-y-2">
              {g.fields.map(([k, lbl]) => (
                <label key={String(k)} className="flex items-center justify-between gap-3 text-sm">
                  <span className="text-slate-500">{lbl}</span>
                  <input type="number" step="any" value={String(s[k] ?? "")}
                    onChange={(e) => set(k, e.target.value)}
                    className="w-32 rounded-lg border border-slate-300 px-2 py-1 text-right font-mono" />
                </label>
              ))}
            </div>
          </section>
        ))}
      </div>

      <section className="mt-4 rounded-xl border border-slate-200 bg-white p-4">
        <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">
          BIR monthly withholding brackets
        </h3>
        <table className="min-w-full text-sm">
          <thead className="text-left text-slate-500">
            <tr><th className="py-1 pr-4">Over</th><th className="py-1 pr-4">Base tax</th><th className="py-1 pr-4">Rate on excess</th></tr>
          </thead>
          <tbody>
            {brackets.map((b) => (
              <tr key={b.id} className="border-t border-slate-100 font-mono">
                <td className="py-1 pr-4">₱{b.lower_bound.toLocaleString()}</td>
                <td className="py-1 pr-4">₱{b.base_tax.toLocaleString()}</td>
                <td className="py-1 pr-4">{(b.rate * 100).toFixed(0)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="mt-2 text-xs text-slate-400">
          TRAIN-law brackets (post-2023). Edit directly in the database if a new
          BIR table takes effect.
        </p>
      </section>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button onClick={() => save()} disabled={saving}
          className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark disabled:opacity-50">
          {saving ? "Saving…" : "Save rates"}
        </button>
        {s.rates_verified
          ? <button onClick={() => save(false)} className="rounded-lg border border-slate-300 px-4 py-2 text-sm">Mark as unverified</button>
          : <button onClick={() => save(true)} className="rounded-lg border border-green-500 px-4 py-2 text-sm text-green-700 hover:bg-green-50">Mark rates verified</button>}
        {s.rates_verified && <span className="text-sm text-green-600">✓ Rates marked verified</span>}
        {msg && <span className="text-sm text-slate-500">{msg}</span>}
      </div>
    </div>
  );
}
