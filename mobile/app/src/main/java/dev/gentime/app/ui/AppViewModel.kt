package dev.gentime.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gentime.app.data.AttendanceRepository
import dev.gentime.app.data.PinManager
import dev.gentime.app.data.Supa
import dev.gentime.app.data.model.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val signedIn: Boolean = false,
    val error: String? = null,
    val pending: Int = 0,
    // App-lock PIN over the persisted session: after email sign-in the user
    // sets a 4–6 digit PIN; later launches only ask for the PIN.
    val hasPin: Boolean = false,
    val locked: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AttendanceRepository(app)
    private val pin = PinManager(app)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val repository get() = repo
    val pinLength get() = pin.length

    init {
        viewModelScope.launch {
            try {
                Supa.client.auth.awaitInitialization()
                refresh(lockIfPinned = true)
            } catch (e: Exception) {
                // Never let session restore crash the app; land on the login screen.
                _state.value = _state.value.copy(loading = false, signedIn = false, error = e.message)
            }
        }
        viewModelScope.launch {
            runCatching {
                repo.pendingCount.collect { n -> _state.value = _state.value.copy(pending = n) }
            }
        }
    }

    private suspend fun refresh(lockIfPinned: Boolean = false) {
        val signedIn = Supa.client.auth.currentUserOrNull() != null
        val profile = if (signedIn) runCatching { repo.currentProfile() }.getOrNull() else null
        _state.value = _state.value.copy(
            loading = false,
            signedIn = signedIn,
            profile = profile,
            hasPin = pin.hasPin(),
            // A restored session behind a PIN starts locked; a fresh email
            // sign-in doesn't.
            locked = lockIfPinned && signedIn && pin.hasPin(),
        )
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                Supa.client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Sign-in failed")
            }
        }
    }

    fun createPin(newPin: String) {
        pin.setPin(newPin)
        _state.value = _state.value.copy(hasPin = true, locked = false)
    }

    fun verifyPin(entry: String): Boolean = pin.verify(entry)
    fun pinLockoutRemainingMs(): Long = pin.lockoutRemainingMs()

    fun unlock() {
        _state.value = _state.value.copy(locked = false)
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { Supa.client.auth.signOut() }
            // Forgetting the PIN too: signing out returns the user to
            // email/password, and the next sign-in sets a fresh PIN.
            pin.clear()
            _state.value = AppState(loading = false, signedIn = false)
        }
    }
}
