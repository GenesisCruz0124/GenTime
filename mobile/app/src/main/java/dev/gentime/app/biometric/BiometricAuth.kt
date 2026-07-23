package dev.gentime.app.biometric

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Wraps AndroidX BiometricPrompt as a suspend call. Accepts a biometric
 * (fingerprint / face) OR the device PIN / pattern / password, so a punch can
 * be verified even when the phone's face unlock isn't exposed to third-party
 * apps — common on many Android OEMs, where only fingerprint reaches
 * BiometricPrompt. The PIN fallback covers everyone with a screen lock set.
 */
object BiometricAuth {

    /**
     * True if the user can verify by biometric OR device credential. A set
     * screen-lock PIN is enough, so this is true on virtually any secured phone.
     */
    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val bm = BiometricManager.from(activity)
        val biometricOk = bm.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        val credentialOk = bm.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        return biometricOk || credentialOk
    }

    /** Returns true on successful authentication, false on cancel/error. */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Confirm it's you",
        subtitle: String = "Verify with biometrics or your PIN to record attendance",
    ): Boolean = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        // A biometric class can be combined with DEVICE_CREDENTIAL in
        // setAllowedAuthenticators only on API 30+. On API 26–29 that
        // combination is rejected there, so enable the credential fallback via
        // the (deprecated) setDeviceCredentialAllowed path instead. In both
        // cases a custom negative button is disallowed alongside the credential
        // option, so none is set — the system provides cancel + "Use PIN".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        prompt.authenticate(builder.build())
    }
}
