import { useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import type { DailyRecord, Profile } from "../lib/types";
import { useAuth } from "../auth/AuthContext";
import StatusBadge from "../components/StatusBadge";

interface Row extends DailyRecord {
  profile?: Pick<Profile, "employee_code" | "full_name">;
}

const iso = (d: Date) => d.toISOString().slice(0, 10);

export default function Attendance() {
  const { profile } = useAuth();
  const isAdmin = profile?.role === "admin";
  const [from, setFrom] = useState(iso(new Date(Date.now() - 7 * 864e5)));
  const [to, setTo] = useState(iso(new Date()));
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    const { data } = await supabase
      .from("daily_records")
      .select("*, profile:profiles!profile_id(employee_code, full_name)")
      .gte("work_date", from)
      .lte("work_date", to)
      .order("work_date", { ascending: false });
    setRows((data as Row[]) ?? []);
    setLoading(false);
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const correct = async (r: Row) => {
    const note = prompt(`Correction note for ${r.profile?.full_name} on ${r.work_date}:`);
    if (!note) return;
    const newStatus = prompt(
      "New status (present/late/absent/on_leave/incomplete):",
      r.status,
    );
    if (!newStatus) return;
    await supabase
      .from("daily_records")
      .update({ status: newStatus, correction_note: note, corrected_by: profile!.id })
      .eq("id", r.id);
    load();
  };

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Attendance (DTR)</h1>
      <div className="mb-4 flex items-end gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">From</span>
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)}
            className="rounded-lg border border-slate-300 px-2 py-1" />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">To</span>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)}
            className="rounded-lg border border-slate-300 px-2 py-1" />
        </label>
        <button onClick={load}
          className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark">
          Apply
        </button>
      </div>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2">Date</th>
              <th className="px-4 py-2">Employee</th>
              <th className="px-4 py-2">First in</th>
              <th className="px-4 py-2">Last out</th>
              <th className="px-4 py-2">Worked</th>
              <th className="px-4 py-2">Late</th>
              <th className="px-4 py-2">Status</th>
              {isAdmin && <th className="px-4 py-2"></th>}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={8} className="px-4 py-6 text-center text-slate-400">Loading…</td></tr>
            ) : rows.length === 0 ? (
              <tr><td colSpan={8} className="px-4 py-6 text-center text-slate-400">No records</td></tr>
            ) : rows.map((r) => (
              <tr key={r.id} className="border-t border-slate-100">
                <td className="px-4 py-2">{r.work_date}</td>
                <td className="px-4 py-2">{r.profile?.full_name}</td>
                <td className="px-4 py-2">{r.first_in ? new Date(r.first_in).toLocaleTimeString() : "—"}</td>
                <td className="px-4 py-2">{r.last_out ? new Date(r.last_out).toLocaleTimeString() : "—"}</td>
                <td className="px-4 py-2">{r.minutes_worked != null ? `${Math.floor(r.minutes_worked / 60)}h ${r.minutes_worked % 60}m` : "—"}</td>
                <td className="px-4 py-2">{r.minutes_late > 0 ? `${r.minutes_late}m` : "—"}</td>
                <td className="px-4 py-2">
                  <StatusBadge status={r.status} />
                  {r.correction_note && <span className="ml-1 text-xs text-slate-400" title={r.correction_note}>✎</span>}
                </td>
                {isAdmin && (
                  <td className="px-4 py-2">
                    <button onClick={() => correct(r)} className="text-xs text-brand hover:underline">correct</button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
