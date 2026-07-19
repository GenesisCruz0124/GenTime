package dev.gentime.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import dev.gentime.app.data.Supa
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * `attachBaseContext` runs before ContentProviders, so installing the crash
 * handler here captures failures during the whole startup — including the
 * provider/init phase — which a handler in onCreate would miss.
 *
 * Implements Configuration.Provider so WorkManager initialises on demand; the
 * androidx.startup auto-init providers are disabled in the manifest to remove
 * an opaque pre-onCreate crash surface.
 */
class GenTimeApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val trace = sw.toString()
                getSharedPreferences("gentime_diag", MODE_PRIVATE)
                    .edit().putString("last_crash", trace).commit()
                // Also written to files/ so it can be retrieved even if the UI
                // never gets far enough to display it.
                runCatching { File(filesDir, "gentime-crash.txt").writeText(trace) }
            }
            prev?.uncaughtException(thread, e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Give the Supabase client an app context so the session persists
        // across restarts (users stay signed in).
        Supa.init(this)
    }
}
