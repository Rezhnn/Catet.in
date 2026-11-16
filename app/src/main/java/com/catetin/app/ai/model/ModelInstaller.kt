package com.catetin.app.ai.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import com.catetin.app.data.db.AssistantModelEntity
import com.catetin.app.data.repo.FinanceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ModelOperation { IDLE, DOWNLOADING, IMPORTING, VERIFYING, DELETING, DONE, ERROR }

data class ModelOperationState(
    val operation: ModelOperation = ModelOperation.IDLE,
    val progress: Float = 0f,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val message: String? = null,
) {
    val busy: Boolean get() = operation in setOf(
        ModelOperation.DOWNLOADING,
        ModelOperation.IMPORTING,
        ModelOperation.VERIFYING,
        ModelOperation.DELETING,
    )
}

@Singleton
class ModelInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FinanceRepository,
) {
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(ModelOperationState())
    val state: StateFlow<ModelOperationState> = _state

    fun modelDirectory(): File = File(context.filesDir, "models").apply { mkdirs() }

    fun installedFile(): File = File(modelDirectory(), ModelCatalog.qwenQ4.fileName)

    suspend fun download() = operationMutex.withLock {
        require(ModelCatalog.qwenQ4.downloadUrl.startsWith("https://")) {
            "URL model belum dikonfigurasi pada build ini"
        }
        val target = installedFile()
        val partial = File(target.path + ".part")
        ensureFreeSpace(ModelCatalog.qwenQ4.sizeBytes - partial.length().coerceAtLeast(0))
        _state.value = ModelOperationState(ModelOperation.DOWNLOADING)
        try {
            withContext(Dispatchers.IO) {
                downloadResumable(partial, target)
                _state.value = _state.value.copy(operation = ModelOperation.VERIFYING)
                val hash = sha256(target)
                if (!hash.equals(ModelCatalog.qwenQ4.sha256, ignoreCase = true)) {
                    target.delete()
                    error("SHA-256 model tidak cocok. Unduhan dibatalkan demi keamanan.")
                }
                require(isGguf(target)) { "Berkas bukan model GGUF yang valid" }
                repository.saveAssistantModel(
                    AssistantModelEntity(
                        modelId = ModelCatalog.qwenQ4.id,
                        variant = ModelCatalog.qwenQ4.variant,
                        filePath = target.absolutePath,
                        sha256 = hash,
                        downloadedAt = System.currentTimeMillis(),
                    ),
                )
            }
            _state.value = ModelOperationState(
                operation = ModelOperation.DONE,
                progress = 1f,
                message = "Model AI siap digunakan di perangkat.",
            )
        } catch (t: Throwable) {
            _state.value = ModelOperationState(
                operation = ModelOperation.ERROR,
                message = t.message ?: "Unduhan model gagal",
            )
            throw t
        }
    }

    suspend fun importFrom(uri: Uri) = operationMutex.withLock {
        _state.value = ModelOperationState(ModelOperation.IMPORTING)
        val target = installedFile()
        val temp = File(target.path + ".import")
        try {
            withContext(Dispatchers.IO) {
                val expected = querySize(uri)
                require(expected == null || expected <= MAX_IMPORT_BYTES) {
                    "Berkas model terlalu besar (maksimal 2,5 GB)"
                }
                ensureFreeSpace(expected ?: MIN_MODEL_BYTES)
                val resolver = context.contentResolver
                resolver.openInputStream(uri)?.use { input ->
                    BufferedInputStream(input).use { source ->
                        BufferedOutputStream(FileOutputStream(temp)).use { output ->
                            copyWithProgress(source, output, expected ?: 0L)
                        }
                    }
                } ?: error("Berkas model tidak dapat dibuka")
                require(temp.length() >= MIN_MODEL_BYTES) { "Berkas terlalu kecil untuk model GGUF" }
                require(isGguf(temp)) { "Format GGUF tidak dikenali" }

                _state.value = _state.value.copy(operation = ModelOperation.VERIFYING, progress = 1f)
                val hash = sha256(temp)
                if (target.exists()) target.delete()
                require(temp.renameTo(target)) { "Model tidak dapat dipindahkan ke penyimpanan aplikasi" }
                repository.saveAssistantModel(
                    AssistantModelEntity(
                        modelId = ModelCatalog.qwenQ4.id,
                        variant = "custom/imported",
                        filePath = target.absolutePath,
                        sha256 = hash,
                        downloadedAt = System.currentTimeMillis(),
                    ),
                )
            }
            _state.value = ModelOperationState(
                operation = ModelOperation.DONE,
                progress = 1f,
                message = "Model GGUF berhasil diimpor dan diverifikasi sebagai berkas.",
            )
        } catch (t: Throwable) {
            temp.delete()
            _state.value = ModelOperationState(
                operation = ModelOperation.ERROR,
                message = t.message ?: "Impor model gagal",
            )
            throw t
        }
    }

    suspend fun delete() = operationMutex.withLock {
        _state.value = ModelOperationState(ModelOperation.DELETING)
        installedFile().delete()
        File(installedFile().path + ".part").delete()
        File(installedFile().path + ".import").delete()
        repository.saveAssistantModel(AssistantModelEntity())
        _state.value = ModelOperationState(ModelOperation.DONE, message = "Model AI dihapus dari perangkat.")
    }

    fun clearResult() {
        if (!_state.value.busy) _state.value = ModelOperationState()
    }

    private suspend fun downloadResumable(partial: File, target: File) {
        var existing = partial.length()
        if (existing > ModelCatalog.qwenQ4.sizeBytes) {
            partial.delete()
            existing = 0L
        }
        if (target.exists() && target.length() == ModelCatalog.qwenQ4.sizeBytes) {
            target.delete()
        }
        val connection = (URL(ModelCatalog.qwenQ4.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Catet.in-Android/1")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK && existing > 0) {
                partial.delete()
                existing = 0L
            } else if (code !in setOf(HttpURLConnection.HTTP_OK, 206, 416)) {
                error("Server model menolak unduhan (HTTP $code)")
            }
            if (code == 416) {
                if (partial.length() == ModelCatalog.qwenQ4.sizeBytes) {
                    require(partial.renameTo(target)) { "Model tidak dapat difinalisasi" }
                    return
                }
                partial.delete()
                error("Unduhan parsial tidak valid; ulangi unduhan")
            }
            val append = code == 206 && existing > 0
            val responseLength = connection.contentLengthLong.coerceAtLeast(0L)
            val total = if (append) existing + responseLength else responseLength
                .takeIf { it > 0 } ?: ModelCatalog.qwenQ4.sizeBytes
            _state.value = _state.value.copy(bytesDone = existing, bytesTotal = total, progress = (existing.toFloat() / total).coerceIn(0f, 1f))
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var done = existing
                    var lastUpdate = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 250) {
                            _state.value = _state.value.copy(
                                bytesDone = done,
                                progress = (done.toFloat() / total).coerceIn(0f, 1f),
                            )
                            lastUpdate = now
                        }
                    }
                    output.fd.sync()
                }
            }
            require(partial.length() == ModelCatalog.qwenQ4.sizeBytes) {
                "Unduhan belum lengkap (${partial.length()} dari ${ModelCatalog.qwenQ4.sizeBytes} byte)"
            }
            _state.value = _state.value.copy(bytesDone = partial.length(), progress = 1f)
            if (target.exists()) target.delete()
            require(partial.renameTo(target)) { "Model tidak dapat difinalisasi" }
        } finally {
            connection.disconnect()
        }
    }

    private fun copyWithProgress(input: BufferedInputStream, output: BufferedOutputStream, total: Long) {
        val buffer = ByteArray(256 * 1024)
        var done = 0L
        var lastUpdate = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            done += read
            val now = System.currentTimeMillis()
            if (now - lastUpdate > 250) {
                _state.value = _state.value.copy(
                    bytesDone = done,
                    bytesTotal = total,
                    progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f,
                )
                lastUpdate = now
            }
        }
        output.flush()
    }

    private fun ensureFreeSpace(needed: Long) {
        val available = modelDirectory().usableSpace
        require(available > needed + 100L * 1024L * 1024L) {
            "Ruang penyimpanan tidak cukup. Butuh sekitar ${ModelCatalog.formatBytes(needed + 100L * 1024L * 1024L)}."
        }
    }

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }.getOrNull()

    @VisibleForTesting
    internal fun isGguf(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        }
    }.getOrDefault(false)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MIN_MODEL_BYTES = 10L * 1024L * 1024L
        const val MAX_IMPORT_BYTES = 2_500L * 1024L * 1024L
    }
}
