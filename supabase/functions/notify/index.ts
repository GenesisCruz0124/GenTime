// Sends an FCM push. Called internally (service role) by DB triggers via pg_net
// on new alerts and leave events. Resolves the recipient's fcm_token, then
// delivers via FCM HTTP v1 using a service-account bearer token.
//
// Env: FCM_PROJECT_ID, FCM_ACCESS_TOKEN (short-lived OAuth token minted by the
// caller/CI), plus the standard SUPABASE_* vars.
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient } from "../_shared/client.ts";

type NotifyBody = {
  recipient_profile_id: string;
  title: string;
  body: string;
  data?: Record<string, string>;
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const payload = (await req.json()) as NotifyBody;
  if (!payload.recipient_profile_id || !payload.title) {
    return json({ error: "missing_fields" }, 400);
  }

  const supabase = serviceClient();
  const { data: profile } = await supabase
    .from("profiles")
    .select("fcm_token")
    .eq("id", payload.recipient_profile_id)
    .single();

  const token = profile?.fcm_token;
  if (!token) return json({ skipped: "no_fcm_token" });

  const projectId = Deno.env.get("FCM_PROJECT_ID");
  const accessToken = Deno.env.get("FCM_ACCESS_TOKEN");
  if (!projectId || !accessToken) {
    // Not configured (e.g. local dev) — no-op rather than failing the trigger.
    return json({ skipped: "fcm_not_configured" });
  }

  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          notification: { title: payload.title, body: payload.body },
          data: payload.data ?? {},
          android: { priority: "high" },
        },
      }),
    },
  );

  if (!res.ok) return json({ error: await res.text() }, 502);
  return json({ sent: true });
});
