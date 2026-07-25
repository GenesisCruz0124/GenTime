// Generates a payroll-ready CSV for a date range. RLS applies via the caller's
// JWT, so supervisors get their team and admins get everyone. The web app can
// also build XLSX client-side from the same report_summary rows.
import { corsHeaders } from "../_shared/cors.ts";
import { userClient } from "../_shared/client.ts";

type Row = {
  employee_code: string;
  full_name: string;
  position: string | null;
  date_hired: string | null;
  employment_type: string | null;
  days_present: number;
  days_late: number;
  total_minutes_late: number;
  days_absent: number;
  days_on_leave: number;
  total_hours: number;
};

const HEADER = [
  "employee_code", "full_name", "position", "date_hired", "employment_type",
  "days_present", "days_late", "total_minutes_late", "days_absent",
  "days_on_leave", "total_hours",
];

function csvCell(v: unknown): string {
  const s = String(v ?? "");
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  const url = new URL(req.url);
  const from = url.searchParams.get("from");
  const to = url.searchParams.get("to");
  const site = url.searchParams.get("site_id");
  if (!from || !to) {
    return new Response(JSON.stringify({ error: "from and to are required" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const supabase = userClient(req);
  const { data, error } = await supabase.rpc("report_summary", {
    p_from: from, p_to: to, p_site_id: site || null,
  });
  if (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const rows = (data ?? []) as Row[];
  const lines = [
    HEADER.join(","),
    ...rows.map((r) => HEADER.map((h) => csvCell((r as Record<string, unknown>)[h])).join(",")),
  ];
  const csv = lines.join("\n");

  return new Response(csv, {
    status: 200,
    headers: {
      ...corsHeaders,
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": `attachment; filename="gentime-report-${from}_${to}.csv"`,
    },
  });
});
