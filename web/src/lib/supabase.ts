import { createClient } from "@supabase/supabase-js";

const url = import.meta.env.VITE_SUPABASE_URL;
const anon = import.meta.env.VITE_SUPABASE_ANON_KEY;

if (!url || !anon) {
  // Surfaces a clear message during local setup instead of an opaque failure.
  console.error("Missing VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY");
}

// Untyped client: the schema is documented in ./types and mapped explicitly at
// each call site, so we keep the client loose rather than generating types.
export const supabase = createClient(url, anon, {
  auth: { persistSession: true, autoRefreshToken: true },
});
