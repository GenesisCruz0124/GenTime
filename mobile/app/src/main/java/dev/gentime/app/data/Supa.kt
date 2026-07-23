package dev.gentime.app.data

import android.content.Context
import dev.gentime.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * Session store backed directly by SharedPreferences + JSON. Self-contained —
 * unlike the library's default SettingsSessionManager it doesn't depend on the
 * androidx.startup context factory (which this app disables), so it can't fail
 * to "create default settings". Keeps users signed in across restarts.
 */
private class PrefsSessionManager(context: Context) : SessionManager {
    private val prefs = context.getSharedPreferences("gentime_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        runCatching {
            prefs.edit().putString(KEY, json.encodeToString(UserSession.serializer(), session)).apply()
        }
    }

    override suspend fun loadSession(): UserSession? =
        prefs.getString(KEY, null)?.let {
            runCatching { json.decodeFromString(UserSession.serializer(), it) }.getOrNull()
        }

    override suspend fun deleteSession() {
        runCatching { prefs.edit().remove(KEY).apply() }
    }

    companion object { private const val KEY = "user_session" }
}

/**
 * Process-wide Supabase client. `init(context)` is called as early as possible
 * (Application.attachBaseContext) so the persistent session manager is ready
 * before the client is first used; falls back to in-memory if it isn't.
 */
object Supa {

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext ?: context
    }

    val client: SupabaseClient by lazy {
        val ctx = appContext
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            requestTimeout = 60.seconds  // 10s default is too tight on mobile
            install(Auth) {
                // BOTH sessionManager and codeVerifierCache must be set. The
                // library resolves each eagerly in AuthImpl's constructor and,
                // when null, builds a Settings-backed default whose factory
                // (createDefaultSettings) throws "Failed to create default
                // settings for SettingsSessionManager" on this app — the
                // androidx.startup context provider it relies on is disabled.
                // Setting only one still crashed via the other.
                sessionManager = if (ctx != null) PrefsSessionManager(ctx) else MemorySessionManager()
                // Email/password sign-in doesn't use PKCE, so an in-memory
                // code-verifier cache is sufficient and never touches Settings.
                codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
