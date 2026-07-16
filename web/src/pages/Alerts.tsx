import { useCallback, useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import type { Alert, Profile } from "../lib/types";
import { useAuth } from "../auth/AuthContext";

interface Row extends Alert {
  profile?: Pick<Profile, "employee_code" | "full_name">;
}

const label: Record<string, string> = {
  outside_geofence: "Left geofence",
  wrong_location_checkin: "Wrong-location check-in",
  missed_checkin: "Missed check-in",
  missed_checkout: "Missed check-out",
};

export default function Alerts() {
  const { profile } = useAuth();
  const [rows, setRows] = useState<Row[]>([]);
  const [showAcked, setShowAcked] = useState(false);

  const load = useCallback(async () => {
    let q = supabase
      .from("alerts")
      .select("*, profile:profiles(employee_code, full_name)")
      .order("created_at", { ascending: false })
      .limit(100);
    if (!showAcked) q = q.is("acknowledged_at", null);
    const { data } = await q;
    setRows((data as Row[]) ?? []);
  }, [showAcked]);

  useEffect(() => { load(); }, [load]);

  const ack = async (r: Row) => {
    await supabase
      .from("alerts")
      .update({ acknowledged_by: profile!.id, acknowledged_at: new Date().toISOString() })
      .eq("id", r.id);
    load();
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Alerts</h1>
        <label className="flex items-center gap-2 text-sm text-slate-500">
          <input type="checkbox" checked={showAcked} onChange={(e) => setShowAcked(e.target.checked)} />
          Show acknowledged
        </label>
      </div>

      <div className="space-y-2">
        {rows.length === 0 && <p className="text-slate-400">No alerts.</p>}
        {rows.map((r) => (
          <div key={r.id} className="flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4">
            <div>
              <div className="flex items-center gap-2">
                <span className="inline-block h-2 w-2 rounded-full bg-red-500" />
                <span className="font-medium">{label[r.alert_type] ?? r.alert_type}</span>
              </div>
              <div className="text-sm text-slate-500">
                {r.profile?.full_name} · {new Date(r.created_at).toLocaleString()}
              </div>
            </div>
            {r.acknowledged_at ? (
              <span className="text-xs text-slate-400">acknowledged</span>
            ) : (
              <button onClick={() => ack(r)}
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50">
                Acknowledge
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
