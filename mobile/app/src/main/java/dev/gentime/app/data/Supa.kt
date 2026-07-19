package dev.gentime.app.data

import dev.gentime.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.minimalSettings
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Process-wide Supabase client. Config injected at build time via BuildConfig.
 *
 * Auth uses `minimalSettings()` — an in-memory session manager rather than the
 * platform (SharedPreferences) store. That keeps the client self-contained (no
 * dependency on an app-context startup initializer) and robust; the trade-off
 * is that the session isn't persisted across app restarts, which is fine here.
 */
object Supa {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth) { minimalSettings() }
            install(Postgrest)
            install(Realtime)
        }
    }
}
