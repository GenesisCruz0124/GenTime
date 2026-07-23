import { useState } from "react";
import * as XLSX from "xlsx";
import { supabase } from "../lib/supabase";
import type { ReportRow } from "../lib/types";

const iso = (d: Date) => d.toISOString().slice(0, 10);

const COLUMNS: (keyof ReportRow)[] = [
  "employee_code", "full_name", "days_present", "days_late",
  "total_minutes_late", "days_absent", "days_on_leave", "total_hours",
];

export default function Reports() {
  const [from, setFrom] = useState(iso(new Date(Date.now() - 30 * 864e5)));
  const [to, setTo] = useState(iso(new Date()));
  const [rows, setRows] = useState<ReportRow[]>([]);
  const [loading, setLoading] = useState(false);

  const run = async () => {
    setLoading(true);
    const { data } = await supabase.rpc("report_summary", {
      p_from: from, p_to: to, p_site_id: null,
    });
    setRows((data as ReportRow[] | null) ?? []);
    setLoading(false);
  };

  const exportCsv = () => {
    const header = COLUMNS.join(",");
    const lines = rows.map((r) =>
      COLUMNS.map((c) => {
        const v = String(r[c] ?? "");
        return /[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v;
      }).join(","),
    );
    download(new Blob([[header, ...lines].join("\n")], { type: "text/csv" }), "csv");
  };

  const exportXlsx = () => {
    const ws = XLSX.utils.json_to_sheet(rows, { header: COLUMNS as string[] });
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Summary");
    const out = XLSX.write(wb, { type: "array", bookType: "xlsx" });
    download(new Blob([out], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }), "xlsx");
  };

  const download = (blob: Blob, ext: string) => {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `gentime-report_${from}_${to}.${ext}`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold">Reports</h1>
      <div className="mb-4 flex flex-wrap items-end gap-3">
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
        <button onClick={run} className="rounded-lg bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark">
          Run report
        </button>
        {rows.length > 0 && (
          <>
            <button onClick={exportCsv} className="rounded-lg border border-slate-300 px-4 py-2 text-sm hover:bg-slate-50">Export CSV</button>
            <button onClick={exportXlsx} className="rounded-lg border border-slate-300 px-4 py-2 text-sm hover:bg-slate-50">Export XLSX</button>
          </>
        )}
      </div>

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-4 py-2">Code</th>
              <th className="px-4 py-2">Name</th>
              <th className="px-4 py-2">Present</th>
              <th className="px-4 py-2">Late</th>
              <th className="px-4 py-2">Late min</th>
              <th className="px-4 py-2">Absent</th>
              <th className="px-4 py-2">On leave</th>
              <th className="px-4 py-2">Hours</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={8} className="px-4 py-6 text-center text-slate-400">Loading…</td></tr>
            ) : rows.length === 0 ? (
              <tr><td colSpan={8} className="px-4 py-6 text-center text-slate-400">Run a report to see results</td></tr>
            ) : rows.map((r) => (
              <tr key={r.profile_id} className="border-t border-slate-100">
                <td className="px-4 py-2 font-mono">{r.employee_code}</td>
                <td className="px-4 py-2">{r.full_name}</td>
                <td className="px-4 py-2">{r.days_present}</td>
                <td className="px-4 py-2">{r.days_late}</td>
                <td className="px-4 py-2">{r.total_minutes_late}</td>
                <td className="px-4 py-2">{r.days_absent}</td>
                <td className="px-4 py-2">{r.days_on_leave}</td>
                <td className="px-4 py-2">{r.total_hours}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
