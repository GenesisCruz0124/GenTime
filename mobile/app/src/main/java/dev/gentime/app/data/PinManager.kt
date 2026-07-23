package dev.gentime.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Local app-unlock PIN. The Supabase session itself is persisted separately
 * (see PrefsSessionManager), so the PIN is only a convenience lock over an
 * already-authenticated session — never the source of truth for auth.
 *
 * Stored as a salted SHA-256 digest in the Keystore-backed encrypted store,
 * falling back to plain prefs on OEM builds where the encrypted store throws
 * (matching Session's resilience). The raw PIN is never written to disk.
 */
class PinManager(context: Context) {

    private val prefs: SharedPreferences = run {
        val app = context.applicationContext
        runCatching {
            EncryptedSharedPreferences.create(
                app,
                "gentime_pin",
                MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            app.getSharedPreferences("gentime_pin_plain", Context.MODE_PRIVATE)
        }
    }

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    /** Digit count of the stored PIN, so the unlock screen knows when to submit. */
    val length: Int get() = prefs.getInt(KEY_LEN, 0)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(pin, salt))
            .putInt(KEY_LEN, pin.length)
            .apply()
    }

    fun verify(pin: String): Boolean {
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        return hash(pin, saltHex.fromHex()) == expected
    }

    fun clear() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_LEN).apply()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_LEN = "pin_len"
    }
}
