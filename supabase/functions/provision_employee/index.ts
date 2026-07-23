// Admin-only: create an auth user + profile for a new employee.
// Verifies the caller is an admin (via their JWT), then uses the service role
// to create the account. The handle_new_user trigger materialises the profile
// from user metadata; we then patch site/supervisor/shift.
import { corsHeaders, json } from "../_shared/cors.ts";
import { serviceClient, userClient } from "../_shared/client.ts";

interface Body {
  email: string;
  password: string;
  employee_code: string;
  full_name: string;
  role?: "employee" | "supervisor" | "admin";
  site_id?: string | null;
  supervisor_id?: string | null;
  shift?: {
    days: number[];
    start_time: string;
    end_time: string;
    grace_minutes?: number;
  } | null;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  // Authorize: caller must be an admin.
  const caller = userClient(req);
  const { data: me } = await caller.rpc("is_admin");
  if (me !== true) return json({ error: "forbidden" }, 403);

  const body = (await req.json()) as Body;
  for (const k of ["email", "password", "employee_code", "full_name"] as const) {
    if (!body[k]) return json({ error: `missing_field:${k}` }, 400);
  }

  const svc = serviceClient();
  const { data: created, error: createErr } = await svc.auth.admin.createUser({
    email: body.email,
    password: body.password,
    email_confirm: true,
    user_metadata: {
      employee_code: body.employee_code,
      full_name: body.full_name,
      role: body.role ?? "employee",
    },
  });
  if (createErr || !created.user) {
    return json({ error: createErr?.message ?? "create_failed" }, 400);
  }

  const uid = created.user.id;
  const { error: patchErr } = await svc
    .from("profiles")
    .update({ site_id: body.site_id ?? null, supervisor_id: body.supervisor_id ?? null })
    .eq("id", uid);
  if (patchErr) return json({ error: patchErr.message }, 400);

  if (body.shift) {
    await svc.from("shifts").insert({
      profile_id: uid,
      days: body.shift.days,
      start_time: body.shift.start_time,
      end_time: body.shift.end_time,
      grace_minutes: body.shift.grace_minutes ?? 10,
    });
  }

  return json({ id: uid }, 201);
});
