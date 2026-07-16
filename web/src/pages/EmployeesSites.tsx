import { useCallback, useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import type { Profile, Site } from "../lib/types";

export default function EmployeesSites() {
  const [tab, setTab] = useState<"employees" | "sites">("employees");
  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Employees &amp; Sites</h1>
      <div className="mb-4 flex gap-2">
        {(["employees", "sites"] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)}
            className={`rounded-lg px-4 py-2 text-sm font-medium capitalize ${
              tab === t ? "bg-brand text-white" : "bg-white border border-slate-200 text-slate-600"
            }`}>
            {t}
          </button>
        ))}
      </div>
      {tab === "employees" ? <Employees /> : <Sites />}
    </div>
  );
}

function Employees() {
  const [rows, setRows] = useState<Profile[]>([]);
  const [sites, setSites] = useState<Site[]>([]);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    const [{ data: p }, { data: s }] = await Promise.all([
      supabase.from("profiles").select("*").order("employee_code"),
      supabase.from("sites").select("*").order("name"),
    ]);
    setRows((p as Profile[]) ?? []);
    setSites((s as Site[]) ?? []);
  }, []);
  useEffect(() => { load(); }, [load]);

  const patch = async (id: string, patch: Partial<Profile>) => {
    await supabase.from("profiles").update(patch).eq("id", id);
    load();
  };

  const resetDevice = async (p: Profile) => {
    if (confirm(`Reset device binding for ${p.full_name}? They can re-bind on next login.`)) {
      patch(p.id, { device_id: null });
    }
  };

  return (
    <div>
      <div className="mb-3">
        <button onClick={() => setCreating((v) => !v)}
          className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark">
          {creating ? "Close" : "+ Provision employee"}
        </button>
      </div>
      {creating && <ProvisionForm sites={sites} people={rows} onDone={() => { setCreating(false); load(); }} />}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2">Code</th>
              <th className="px-4 py-2">Name</th>
              <th className="px-4 py-2">Role</th>
              <th className="px-4 py-2">Site</th>
              <th className="px-4 py-2">Device</th>
              <th className="px-4 py-2">Active</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((p) => (
              <tr key={p.id} className="border-t border-slate-100">
                <td className="px-4 py-2 font-mono">{p.employee_code}</td>
                <td className="px-4 py-2">{p.full_name}</td>
                <td className="px-4 py-2 capitalize">{p.role}</td>
                <td className="px-4 py-2">
                  <select value={p.site_id ?? ""} onChange={(e) => patch(p.id, { site_id: e.target.value || null })}
                    className="rounded border border-slate-200 px-1 py-0.5 text-xs">
                    <option value="">—</option>
                    {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                  </select>
                </td>
                <td className="px-4 py-2">{p.device_id ? "bound" : "—"}</td>
                <td className="px-4 py-2">
                  <input type="checkbox" checked={p.is_active}
                    onChange={(e) => patch(p.id, { is_active: e.target.checked })} />
                </td>
                <td className="px-4 py-2">
                  {p.device_id && (
                    <button onClick={() => resetDevice(p)} className="text-xs text-brand hover:underline">
                      reset device
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ProvisionForm({ sites, people, onDone }: {
  sites: Site[]; people: Profile[]; onDone: () => void;
}) {
  const [f, setF] = useState({
    email: "", password: "", employee_code: "", full_name: "",
    role: "employee", site_id: "", supervisor_id: "",
  });
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const supervisors = people.filter((p) => p.role === "supervisor" || p.role === "admin");

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true); setErr(null);
    const { error } = await supabase.functions.invoke("provision_employee", {
      body: {
        ...f,
        site_id: f.site_id || null,
        supervisor_id: f.supervisor_id || null,
        shift: { days: [2, 3, 4, 5, 6], start_time: "09:00", end_time: "18:00", grace_minutes: 10 },
      },
    });
    setBusy(false);
    if (error) { setErr(error.message); return; }
    onDone();
  };

  const input = (key: keyof typeof f, placeholder: string, type = "text") => (
    <input type={type} placeholder={placeholder} value={f[key]}
      onChange={(e) => setF({ ...f, [key]: e.target.value })}
      className="rounded-lg border border-slate-300 px-3 py-2 text-sm" required={key !== "site_id" && key !== "supervisor_id"} />
  );

  return (
    <form onSubmit={submit} className="mb-4 grid grid-cols-2 gap-3 rounded-xl border border-slate-200 bg-white p-4 lg:grid-cols-3">
      {input("employee_code", "Employee code (GT-000X)")}
      {input("full_name", "Full name")}
      {input("email", "Email", "email")}
      {input("password", "Temp password", "password")}
      <select value={f.role} onChange={(e) => setF({ ...f, role: e.target.value })}
        className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
        <option value="employee">employee</option>
        <option value="supervisor">supervisor</option>
        <option value="admin">admin</option>
      </select>
      <select value={f.site_id} onChange={(e) => setF({ ...f, site_id: e.target.value })}
        className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
        <option value="">— site —</option>
        {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
      </select>
      <select value={f.supervisor_id} onChange={(e) => setF({ ...f, supervisor_id: e.target.value })}
        className="rounded-lg border border-slate-300 px-3 py-2 text-sm">
        <option value="">— supervisor —</option>
        {supervisors.map((s) => <option key={s.id} value={s.id}>{s.full_name}</option>)}
      </select>
      {err && <p className="col-span-full text-sm text-red-600">{err}</p>}
      <button type="submit" disabled={busy}
        className="col-span-full rounded-lg bg-brand py-2 text-sm font-medium text-white hover:bg-brand-dark disabled:opacity-50">
        {busy ? "Creating…" : "Create account"}
      </button>
    </form>
  );
}

function Sites() {
  const [rows, setRows] = useState<Site[]>([]);
  const [f, setF] = useState({ name: "", lat: "", lng: "", radius_m: "100" });

  const load = useCallback(async () => {
    const { data } = await supabase.from("sites").select("*").order("name");
    setRows((data as Site[]) ?? []);
  }, []);
  useEffect(() => { load(); }, [load]);

  const create = async (e: React.FormEvent) => {
    e.preventDefault();
    await supabase.from("sites").insert({
      name: f.name, lat: Number(f.lat), lng: Number(f.lng), radius_m: Number(f.radius_m),
    });
    setF({ name: "", lat: "", lng: "", radius_m: "100" });
    load();
  };

  const patch = async (id: string, patch: Partial<Site>) => {
    await supabase.from("sites").update(patch).eq("id", id);
    load();
  };

  const remove = async (s: Site) => {
    if (confirm(`Delete site "${s.name}"?`)) {
      await supabase.from("sites").delete().eq("id", s.id);
      load();
    }
  };

  return (
    <div>
      <form onSubmit={create} className="mb-4 flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4">
        <input placeholder="Site name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} required
          className="rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        <input placeholder="Lat" value={f.lat} onChange={(e) => setF({ ...f, lat: e.target.value })} required
          className="w-28 rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        <input placeholder="Lng" value={f.lng} onChange={(e) => setF({ ...f, lng: e.target.value })} required
          className="w-28 rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        <input placeholder="Radius (m)" value={f.radius_m} onChange={(e) => setF({ ...f, radius_m: e.target.value })}
          className="w-28 rounded-lg border border-slate-300 px-3 py-2 text-sm" />
        <button className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark">Add site</button>
      </form>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2">Name</th>
              <th className="px-4 py-2">Lat</th>
              <th className="px-4 py-2">Lng</th>
              <th className="px-4 py-2">Radius (m)</th>
              <th className="px-4 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((s) => (
              <tr key={s.id} className="border-t border-slate-100">
                <td className="px-4 py-2">{s.name}</td>
                <td className="px-4 py-2 font-mono">{s.lat.toFixed(5)}</td>
                <td className="px-4 py-2 font-mono">{s.lng.toFixed(5)}</td>
                <td className="px-4 py-2">
                  <input type="number" defaultValue={s.radius_m}
                    onBlur={(e) => patch(s.id, { radius_m: Number(e.target.value) })}
                    className="w-20 rounded border border-slate-200 px-1 py-0.5 text-xs" />
                </td>
                <td className="px-4 py-2">
                  <button onClick={() => remove(s)} className="text-xs text-red-600 hover:underline">delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
