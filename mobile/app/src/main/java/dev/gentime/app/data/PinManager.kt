package dev.gentime.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.min
import kotlin.math.pow

/**
 * Local app-unlock PIN. The Supabase session itself is persisted separately
 * (see PrefsSessionManager), so the PIN is only a convenience lock over an
 * already-authenticated session — never the source of truth for auth.
 *
 * Stored as a salted PBKDF2 digest in the Keystore-backed encrypted store,
 * falling back to plain prefs on OEM builds where the encrypted store throws
 * (matching Session's resilience). The raw PIN is never written to disk.
 *
 * A 4-6 digit PIN is only 10,000-1,000,000 combinations, so unthrottled
 * retries would make it brute-forceable in seconds by anyone holding the
 * unlocked device. [verify] enforces an escalating lockout after repeated
 * failures, mirroring the platform's own lock-screen behaviour.
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

    /**
     * Milliseconds until [verify] will accept attempts again, or 0 if unlocked.
     * The unlock screen should poll/display this rather than letting the user
     * submit while locked out.
     */
    fun lockoutRemainingMs(): Long {
        val until = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(pin, salt).toHex())
            .putInt(KEY_LEN, pin.length)
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    /**
     * Returns true iff the PIN matches AND no lockout is in effect. Every
     * wrong guess counts toward an escalating lockout; a correct guess resets
     * it. Locked-out calls still count as failures so an attacker can't
     * "peek" past the lockout boundary by racing the clock.
     */
    fun verify(pin: String): Boolean {
        if (lockoutRemainingMs() > 0) return false

        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        val matches = MessageDigest.isEqual(hash(pin, saltHex.fromHex()), expected.fromHex())

        if (matches) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAILS, fails)
            if (fails >= FREE_ATTEMPTS) {
                val backoffMs = min(
                    MAX_LOCKOUT_MS,
                    (BASE_LOCKOUT_MS * 2.0.pow(fails - FREE_ATTEMPTS)).toLong(),
                )
                editor.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + backoffMs)
            }
            editor.apply()
        }
        return matches
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_HASH).remove(KEY_SALT).remove(KEY_LEN)
            .remove(KEY_FAILS).remove(KEY_LOCK_UNTIL)
            .apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_LEN = "pin_len"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_LOCK_UNTIL = "pin_lock_until"

        private const val PBKDF2_ITERATIONS = 120_000

        // First 4 wrong guesses are free (typos happen); the 5th onward
        // triggers a doubling lockout: 5th->30s, 6th->60s, 7th->120s, capped.
        private const val FREE_ATTEMPTS = 4
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 15L * 60_000L
    }
}
