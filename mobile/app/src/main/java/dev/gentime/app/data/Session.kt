package dev.gentime.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Per-install identity stored in EncryptedSharedPreferences. `deviceId` backs
 * the one-active-device-per-employee binding enforced server-side.
 */
class Session(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "gentime_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Stable device id generated once on first access. */
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
