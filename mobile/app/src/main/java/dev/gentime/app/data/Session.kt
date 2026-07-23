package dev.gentime.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Per-install identity. `deviceId` backs the one-active-device-per-employee
 * binding enforced server-side.
 *
 * Prefs are encrypted when the device supports it, but fall back to plain
 * SharedPreferences if the Keystore-backed store can't be created — some OEM
 * builds throw from EncryptedSharedPreferences, and a crash at startup is worse
 * than storing a random UUID in the clear.
 */
class Session(context: Context) {

    private val prefs: SharedPreferences = run {
        val app = context.applicationContext
        runCatching {
            EncryptedSharedPreferences.create(
                app,
                "gentime_secure",
                MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            app.getSharedPreferences("gentime_plain", Context.MODE_PRIVATE)
        }
    }

    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    var onTheClock: Boolean
        get() = prefs.getBoolean(KEY_ON_CLOCK, false)
        set(v) = prefs.edit().putBoolean(KEY_ON_CLOCK, v).apply()

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_ON_CLOCK = "on_the_clock"
    }
}
