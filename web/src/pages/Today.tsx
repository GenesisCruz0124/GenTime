import { useEffect, useState } from "react";
import { supabase } from "../lib/supabase";
import type { DailyRecord, Profile } from "../lib/types";
import StatusBadge from "../components/StatusBadge";

const today = () => new Date().toISOString().slice(0, 10);

interface Row extends DailyRecord {
  profile?: Pick<Profile, "employee_code" | "full_name">;
}

export default function Today() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from("daily_records")
        .select("*, profile:profiles(employee_code, full_name)")
        .eq("work_date", today());
      setRows((data as Row[]) ?? []);
      setLoading(false);
    })();
  }, []);

  const buckets = {
    present: rows.filter((r) => r.status === "present"),
    late: rows.filter((r) => r.status === "late"),
    absent: rows.filter((r) => r.status === "absent"),
    on_leave: rows.filter((r) => r.status === "on_leave"),
    incomplete: rows.filter((r) => r.status === "incomplete"),
  };

  return (
    <div>
      <h1 className="mb-1 text-2xl font-semibold">Today</h1>
      <p className="mb-6 text-sm text-slate-500">{today()}</p>

      {loading ? (
        <p className="text-slate-500">Loading…</p>
      ) : (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
          {(Object.keys(buckets) as (keyof typeof buckets)[]).map((k) => (
            <div key={k} className="rounded-xl border border-slate-200 bg-white p-4">
              <div className="mb-2 flex items-center justify-between">
                <StatusBadge status={k} />
                <span className="text-2xl font-bold">{buckets[k].length}</span>
              </div>
              <ul className="space-y-1 text-sm">
                {buckets[k].map((r) => (
                  <li key={r.id} className="flex justify-between text-slate-600">
                    <span>{r.profile?.full_name ?? r.profile_id.slice(0, 8)}</span>
                    {r.minutes_late > 0 && (
                      <span className="text-amber-600">+{r.minutes_late}m</span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
