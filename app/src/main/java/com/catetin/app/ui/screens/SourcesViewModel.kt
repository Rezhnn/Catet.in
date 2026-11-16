package com.catetin.app.ui.screens

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.app.ingestion.gmail.GmailAuthManager
import com.catetin.app.ingestion.gmail.GmailSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException

data class SourcesUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val authManager: GmailAuthManager,
    private val repository: FinanceRepository,
    private val scheduler: GmailSyncScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(SourcesUiState())
    val state: StateFlow<SourcesUiState> = _state

    fun isGmailConfigured(): Boolean = authManager.isConfigured()

    fun createGmailSignInIntent(): Intent? = runCatching { authManager.createSignInIntent() }
        .onFailure { _state.value = SourcesUiState(message = authMessage(it), error = true) }
        .getOrNull()

    fun completeGmailSignIn(result: Intent) {
        viewModelScope.launch {
            _state.value = SourcesUiState(busy = true)
            runCatching {
                authManager.completeSignIn(result)
                scheduler.ensurePeriodic()
                scheduler.syncNow()
            }.onSuccess {
                _state.value = SourcesUiState(message = "Gmail terhubung. Sinkronisasi pertama dimulai.")
            }.onFailure {
                _state.value = SourcesUiState(message = authMessage(it), error = true)
            }
        }
    }

    fun syncNow() {
        scheduler.ensurePeriodic()
        scheduler.syncNow()
        _state.value = SourcesUiState(message = "Sinkronisasi Gmail dimulai di latar belakang.")
    }

    fun disconnect(deleteImportedEmailData: Boolean) {
        viewModelScope.launch {
            _state.value = SourcesUiState(busy = true)
            runCatching {
                scheduler.cancel()
                authManager.disconnect()
                if (deleteImportedEmailData) repository.deleteGmailDerivedData()
            }.onSuccess {
                _state.value = SourcesUiState(
                    message = if (deleteImportedEmailData) "Gmail diputuskan dan data email dihapus." else "Gmail diputuskan. Data lama tetap tersimpan.",
                )
            }.onFailure {
                _state.value = SourcesUiState(message = authMessage(it), error = true)
            }
        }
    }

    fun addGmailSender(pattern: String, label: String, onDone: () -> Unit) {
        val normalized = normalizeSender(pattern)
        if (normalized == null) {
            _state.value = SourcesUiState(message = "Masukkan alamat email atau domain yang valid", error = true)
            return
        }
        viewModelScope.launch {
            repository.addGmailSender(normalized, label)
            onDone()
            _state.value = SourcesUiState(message = "Pengirim Gmail ditambahkan")
        }
    }

    fun setGmailSenderEnabled(pattern: String, enabled: Boolean) {
        viewModelScope.launch { repository.setGmailSenderEnabled(pattern, enabled) }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = false)
    }

    private fun normalizeSender(raw: String): String? {
        val value = raw.trim().lowercase(Locale.ROOT).removePrefix("@").removePrefix("from:")
        if (value.length !in 3..160 || !SENDER.matches(value)) return null
        return value
    }

    private fun authMessage(t: Throwable): String = when (t) {
        is AuthorizationException -> when {
            t.code == AuthorizationException.GeneralErrors.NETWORK_ERROR.code -> "Tidak dapat menghubungi Google. Periksa koneksi internet."
            t.code == AuthorizationException.AuthorizationRequestErrors.ACCESS_DENIED.code -> "Izin Gmail dibatalkan."
            else -> "Otorisasi Gmail gagal: ${t.errorDescription ?: t.error ?: "kesalahan OAuth"}"
        }
        else -> t.message?.take(240) ?: "Operasi Gmail gagal"
    }

    private companion object {
        val SENDER = Regex("^[a-z0-9][a-z0-9._+\\-]*(@[a-z0-9.-]+)?$")
    }
}
