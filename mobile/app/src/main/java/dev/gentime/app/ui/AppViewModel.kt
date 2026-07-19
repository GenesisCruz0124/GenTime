package dev.gentime.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.gentime.app.data.AttendanceRepository
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
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AttendanceRepository(app)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    val repository get() = repo

    init {
        viewModelScope.launch {
            try {
                Supa.client.auth.awaitInitialization()
                refresh()
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

    private suspend fun refresh() {
        val signedIn = Supa.client.auth.currentUserOrNull() != null
        val profile = if (signedIn) runCatching { repo.currentProfile() }.getOrNull() else null
        _state.value = _state.value.copy(loading = false, signedIn = signedIn, profile = profile)
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

    fun signOut() {
        viewModelScope.launch {
            runCatching { Supa.client.auth.signOut() }
            _state.value = AppState(loading = false, signedIn = false)
        }
    }
}
