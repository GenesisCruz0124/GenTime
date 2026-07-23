// Scheduled: raise missed_checkin alerts once a scheduled shift's grace elapses
// with no check-in.
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient } from "../_shared/client.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const supabase = serviceClient();
  const { data, error } = await supabase.rpc("missed_checkin_monitor");
  if (error) return json({ error: error.message }, 500);
  return json({ alerts_raised: data });
});
