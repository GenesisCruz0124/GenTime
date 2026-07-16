import { useCallback, useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import type { LeaveRequest, Profile } from "../lib/types";
import { useAuth } from "../auth/AuthContext";

interface Row extends LeaveRequest {
  profile?: Pick<Profile, "employee_code" | "full_name">;
}

export default function LeaveApprovals() {
  const { profile } = useAuth();
  const [rows, setRows] = useState<Row[]>([]);
  const [filter, setFilter] = useState<"pending" | "all">("pending");

  const load = useCallback(async () => {
    let q = supabase
      .from("leave_requests")
      .select("*, profile:profiles(employee_code, full_name)")
      .order("created_at", { ascending: false });
    if (filter === "pending") q = q.eq("status", "pending");
    const { data } = await q;
    setRows((data as Row[]) ?? []);
  }, [filter]);

  useEffect(() => { load(); }, [load]);

  const decide = async (r: Row, status: "approved" | "rejected") => {
    await supabase
      .from("leave_requests")
      .update({ status, decided_by: profile!.id, decided_at: new Date().toISOString() })
      .eq("id", r.id);
    load();
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Leave Approvals</h1>
        <select value={filter} onChange={(e) => setFilter(e.target.value as "pending" | "all")}
          className="rounded-lg border border-slate-300 px-2 py-1 text-sm">
          <option value="pending">Pending</option>
          <option value="all">All</option>
        </select>
      </div>

      <div className="space-y-3">
        {rows.length === 0 && <p className="text-slate-400">Nothing to show.</p>}
        {rows.map((r) => (
          <div key={r.id} className="flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4">
            <div>
              <div className="font-medium">{r.profile?.full_name}
                <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs capitalize">{r.leave_type}</span>
              </div>
              <div className="text-sm text-slate-500">{r.date_from} → {r.date_to}</div>
              {r.reason && <div className="mt-1 text-sm text-slate-600">{r.reason}</div>}
            </div>
            {r.status === "pending" ? (
              <div className="flex gap-2">
                <button onClick={() => decide(r, "approved")}
                  className="rounded-lg bg-green-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-green-700">Approve</button>
                <button onClick={() => decide(r, "rejected")}
                  className="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700">Reject</button>
              </div>
            ) : (
              <span className="rounded-full bg-slate-100 px-3 py-1 text-sm capitalize text-slate-600">{r.status}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
