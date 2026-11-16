package com.catetin.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetin.app.backup.BackupManager
import com.catetin.app.backup.CsvExporter
import com.catetin.app.data.prefs.AppSettings
import com.catetin.app.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val awaitingRestore: Boolean = false,
    val pendingFileName: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val csvExporter: CsvExporter,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    private var pendingBackup: String? = null

    fun setIncludeRawText(value: Boolean) {
        viewModelScope.launch { settingsRepository.setIncludeRawTextInBackup(value) }
    }

    fun export(uri: Uri, includeRawText: Boolean) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            val result = runCatching {
                val json = backupManager.createJson(includeRawText)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(json.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("Berkas tujuan tidak dapat dibuka")
                }
            }
            _state.value = result.fold(
                onSuccess = { BackupUiState(message = "Cadangan berhasil dibuat. Berkas tidak berisi kredensial Gmail atau model AI.") },
                onFailure = { BackupUiState(message = it.userMessage(), isError = true) },
            )
        }
    }

    fun selectRestore(uri: Uri) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val name = displayName(uri) ?: "berkas cadangan"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Berkas tidak dapat dibuka")
                    require(bytes.size <= 25 * 1024 * 1024) { "Berkas cadangan terlalu besar (maksimal 25 MB)" }
                    name to String(bytes, StandardCharsets.UTF_8)
                }
            }
            result.fold(
                onSuccess = { (name, raw) ->
                    pendingBackup = raw
                    _state.value = BackupUiState(awaitingRestore = true, pendingFileName = name)
                },
                onFailure = { _state.value = BackupUiState(message = it.userMessage(), isError = true) },
            )
        }
    }

    fun exportCsv(uri: Uri) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = BackupUiState(busy = true)
            val result = runCatching {
                val csv = csvExporter.createCsv()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(csv.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("Berkas CSV tidak dapat dibuat")
                }
            }
            _state.value = result.fold(
                onSuccess = { BackupUiState(message = "CSV transaksi berhasil dibuat.") },
                onFailure = { BackupUiState(message = it.userMessage(), isError = true) },
            )
        }
    }

    fun cancelRestore() {
        pendingBackup = null
        _state.value = BackupUiState()
    }

    fun confirmRestore() {
        val raw = pendingBackup ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            val result = backupManager.restore(raw)
            result.fold(
                onSuccess = { backup ->
                    settingsRepository.applyBackup(backup.settings)
                    pendingBackup = null
                    _state.value = BackupUiState(
                        message = "Cadangan dipulihkan: ${backup.transactions.size} transaksi dan ${backup.sourceEvents.size} sumber data.",
                    )
                },
                onFailure = {
                    _state.value = BackupUiState(message = it.userMessage(), isError = true)
                },
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, isError = false)
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun Throwable.userMessage(): String = when (this) {
        is IllegalArgumentException -> message ?: "Berkas tidak valid"
        else -> message?.takeIf { it.isNotBlank() } ?: "Terjadi kesalahan saat memproses cadangan"
    }
}
