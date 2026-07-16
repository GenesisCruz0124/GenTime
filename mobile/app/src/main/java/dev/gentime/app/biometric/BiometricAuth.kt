package dev.gentime.app.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Wraps AndroidX BiometricPrompt as a suspend call. Prefers BIOMETRIC_STRONG
 * and falls back to WEAK, matching the spec.
 */
object BiometricAuth {

    private const val ALLOWED =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(ALLOWED) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Returns true on successful authentication, false on cancel/error. */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Confirm it's you",
        subtitle: String = "Verify to record your attendance",
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
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED)
            .setNegativeButtonText("Cancel")
            .build()
        prompt.authenticate(info)
    }
}
