// Runs compute_daily_records for a given date (default: yesterday, UTC).
// Invoked by the scheduler or on-demand by an admin (service role).
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient } from "../_shared/client.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  let date: string | null = null;
  if (req.method === "POST") {
    try {
      const body = await req.json();
      date = body?.date ?? null;
    } catch { /* no body -> default date */ }
  }

  const supabase = serviceClient();
  const { data, error } = await supabase.rpc("compute_daily_records", date ? { p_date: date } : {});
  if (error) return json({ error: error.message }, 500);
  return json({ processed: data });
});
