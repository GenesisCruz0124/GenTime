// Edge Function wrapper around the submit_attendance_event RPC.
// The mobile app may call this HTTP endpoint or the RPC directly; both run the
// same SECURITY DEFINER logic (device binding, idempotency, geofence, alerts).
import { corsHeaders, json } from "../_shared/cors.ts";
import { userClient } from "../_shared/client.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const required = ["client_event_id", "event_type", "event_at", "device_id"];
  for (const k of required) {
    if (body[k] === undefined || body[k] === null) {
      return json({ error: `missing_field:${k}` }, 400);
    }
  }

  const supabase = userClient(req);
  const { data, error } = await supabase.rpc("submit_attendance_event", {
    p_client_event_id: body.client_event_id,
    p_event_type: body.event_type,
    p_event_at: body.event_at,
    p_lat: body.lat ?? null,
    p_lng: body.lng ?? null,
    p_accuracy_m: body.accuracy_m ?? null,
    p_device_id: body.device_id,
    p_is_offline_sync: body.is_offline_sync ?? false,
  });

  if (error) {
    const status = error.message?.includes("device_mismatch") ? 409 : 400;
    return json({ error: error.message }, status);
  }
  return json({ event: data }, 201);
});
