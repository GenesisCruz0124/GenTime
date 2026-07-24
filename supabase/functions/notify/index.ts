// Sends an FCM push. Called internally (service role) by DB triggers via pg_net
// on new alerts and leave events. Resolves the recipient's fcm_token, then
// delivers via FCM HTTP v1 using an access token minted from a service
// account (JWT-bearer flow) — no external process has to mint or refresh
// anything, since Google access tokens expire hourly.
//
// Env: FCM_PROJECT_ID, FCM_SERVICE_ACCOUNT_JSON (the raw JSON key downloaded
// from Firebase Console > Project Settings > Service Accounts), plus the
// standard SUPABASE_* vars.
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient } from "../_shared/client.ts";

type NotifyBody = {
  recipient_profile_id: string;
  title: string;
  body: string;
  data?: Record<string, string>;
};

const b64url = (bytes: ArrayBuffer | Uint8Array) => {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let str = "";
  for (const b of arr) str += String.fromCharCode(b);
  return btoa(str).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
};
const b64urlJson = (obj: unknown) => b64url(new TextEncoder().encode(JSON.stringify(obj)));

// Cached across warm invocations of this isolate; refreshed a minute before
// actual expiry so a request never races an expiring token.
let cachedToken: { token: string; expiresAt: number } | null = null;

async function getAccessToken(serviceAccountJson: string): Promise<string> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) {
    return cachedToken.token;
  }

  const sa = JSON.parse(serviceAccountJson) as { client_email: string; private_key: string };
  const now = Math.floor(Date.now() / 1000);
  const unsigned = `${b64urlJson({ alg: "RS256", typ: "JWT" })}.${
    b64urlJson({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    })
  }`;

  const pemBody = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const key = await crypto.subtle.importKey(
    "pkcs8",
    Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0)),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  );
  const jwt = `${unsigned}.${b64url(sig)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`token_exchange_failed: ${await res.text()}`);
  const body = await res.json();
  cachedToken = { token: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
  return cachedToken.token;
}

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
  const serviceAccountJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
  if (!projectId || !serviceAccountJson) {
    // Not configured (e.g. local dev) — no-op rather than failing the trigger.
    return json({ skipped: "fcm_not_configured" });
  }

  let accessToken: string;
  try {
    accessToken = await getAccessToken(serviceAccountJson);
  } catch (e) {
    return json({ error: `fcm_auth_failed: ${(e as Error).message}` }, 502);
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
