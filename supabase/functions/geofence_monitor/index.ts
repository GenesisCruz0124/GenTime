// Scheduled: evaluate on-the-clock employees against their site radius and
// raise outside_geofence alerts (debounced in the RPC).
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient } from "../_shared/client.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  const supabase = serviceClient();
  const { data, error } = await supabase.rpc("geofence_monitor");
  if (error) return json({ error: error.message }, 500);
  return json({ alerts_raised: data });
});
