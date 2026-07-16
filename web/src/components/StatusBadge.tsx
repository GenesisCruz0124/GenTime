import type { DailyStatus } from "../lib/types";

const styles: Record<string, string> = {
  present: "bg-green-100 text-green-800",
  late: "bg-amber-100 text-amber-800",
  absent: "bg-red-100 text-red-800",
  on_leave: "bg-blue-100 text-blue-800",
  incomplete: "bg-orange-100 text-orange-800",
  pending: "bg-slate-100 text-slate-600",
};

export default function StatusBadge({ status }: { status: DailyStatus | string }) {
  return (
    <span
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
        styles[status] ?? "bg-slate-100 text-slate-600"
      }`}
    >
      {String(status).replace("_", " ")}
    </span>
  );
}
