// Scheduled daily: delete location_pings older than 7 days (retention policy).
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient } from "../_shared/client.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const supabase = serviceClient();
  const { data, error } = await supabase.rpc("purge_old_pings");
  if (error) return json({ error: error.message }, 500);
  return json({ deleted: data });
});
