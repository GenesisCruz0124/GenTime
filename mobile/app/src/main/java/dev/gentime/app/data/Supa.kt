package dev.gentime.app.data

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import dev.gentime.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.minimalSettings
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlin.time.Duration.Companion.seconds

/**
 * Process-wide Supabase client. Config injected at build time via BuildConfig.
 *
 * The session is persisted in SharedPreferences (via a SettingsSessionManager)
 * so users stay signed in across app restarts. `init(context)` must be called
 * from Application.onCreate before the client is first used; if it isn't, we
 * fall back to an in-memory session rather than crashing.
 */
object Supa {

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            // Default 10s is too tight on slow mobile connections.
            requestTimeout = 60.seconds
            install(Auth) {
                val ctx = appContext
                if (ctx != null) {
                    val prefs = ctx.getSharedPreferences("gentime_session", Context.MODE_PRIVATE)
                    sessionManager = SettingsSessionManager(SharedPreferencesSettings(prefs))
                } else {
                    minimalSettings()
                }
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
