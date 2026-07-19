package dev.gentime.app

import android.app.Application
import java.io.PrintWriter
import java.io.StringWriter

class GenTimeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Persist any uncaught crash so the next launch can show it on screen
        // (debug aid — a silent close tells us nothing).
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                getSharedPreferences("gentime_diag", MODE_PRIVATE)
                    .edit().putString("last_crash", sw.toString()).commit()
            }
            prev?.uncaughtException(thread, e)
        }
    }
}
