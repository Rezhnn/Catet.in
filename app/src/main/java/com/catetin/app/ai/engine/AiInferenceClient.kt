package com.catetin.app.ai.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.catetin.app.ai.prompt.AssistantSystemPrompt
import com.catetin.app.ai.router.LlmIntentParser
import com.catetin.app.ai.router.QueryIntent
import com.catetin.app.ai.service.AiInferenceService
import com.catetin.app.ai.service.AiProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Singleton
class AiInferenceClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalLlmEngine {
    private val requestIds = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Bundle>>()
    private val bindMutex = Mutex()
    private val generationMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _status = MutableStateFlow(AiEngineStatus())
    override val status: StateFlow<AiEngineStatus> = _status

    @Volatile private var messenger: Messenger? = null
    @Volatile private var binding: ServiceConnection? = null
    @Volatile private var connectWaiter: CompletableDeferred<Messenger>? = null

    private val replyMessenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != AiProtocol.MSG_REPLY) return
            val data = msg.data ?: return
            val id = data.getInt(AiProtocol.KEY_REQUEST_ID)
            val waiter = pending.remove(id) ?: return
            if (data.getBoolean(AiProtocol.KEY_OK, false)) {
                waiter.complete(data)
            } else {
                waiter.completeExceptionally(
                    IllegalStateException(data.getString(AiProtocol.KEY_ERROR) ?: "Proses AI tidak merespons"),
                )
            }
        }
    })

    override suspend fun load(modelPath: String) {
        _status.value = AiEngineStatus(AiEngineState.LOADING, "Memuat Qwen3.5-0.8B…")
        runCatching {
            val service = connect()
            val data = request(
                service,
                AiProtocol.MSG_LOAD,
                Bundle().apply { putString(AiProtocol.KEY_MODEL_PATH, modelPath) },
                timeoutMs = 120_000,
            )
            check(data.getString(AiProtocol.KEY_STATE) == "READY")
        }.onSuccess {
            _status.value = AiEngineStatus(AiEngineState.READY, "Siap · model lokal")
        }.onFailure { t ->
            _status.value = AiEngineStatus(AiEngineState.ERROR, t.message ?: "Model gagal dimuat")
            throw t
        }
    }

    override suspend fun generateIntent(question: String, categories: List<String>): Result<QueryIntent> =
        generationMutex.withLock {
            runCatching {
                check(_status.value.state == AiEngineState.READY) { "Model AI belum siap" }
                val service = connect()
                val system = AssistantSystemPrompt.build(categories)
                val first = complete(
                    service,
                    prompt = AssistantSystemPrompt.userPrompt(question),
                    system = system,
                )
                LlmIntentParser.parse(first, categories).getOrElse { firstError ->
                    val repaired = complete(
                        service,
                        prompt = AssistantSystemPrompt.repairPrompt(first),
                        system = system,
                    )
                    LlmIntentParser.parse(repaired, categories).getOrElse {
                        throw IllegalArgumentException(
                            "Model tidak menghasilkan intent yang valid: ${firstError.message}",
                            it,
                        )
                    }
                }
            }.recoverCatching { t ->
                if (_status.value.state == AiEngineState.READY) {
                    throw IllegalStateException("Model AI tidak dapat memahami pertanyaan ini", t)
                }
                throw t
            }
        }

    private suspend fun complete(service: Messenger, prompt: String, system: String): String {
        val data = request(
            service,
            AiProtocol.MSG_GENERATE,
            Bundle().apply {
                putString(AiProtocol.KEY_PROMPT, prompt)
                putString(AiProtocol.KEY_SYSTEM_PROMPT, system)
                putInt(AiProtocol.KEY_MAX_TOKENS, 180)
            },
            timeoutMs = 90_000,
        )
        return data.getString(AiProtocol.KEY_TEXT).orEmpty()
    }

    private suspend fun connect(): Messenger {
        messenger?.let { return it }
        return bindMutex.withLock {
            messenger?.let { return@withLock it }
            val waiter = CompletableDeferred<Messenger>()
            connectWaiter = waiter
            withContext(Dispatchers.Main.immediate) {
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val serviceMessenger = binder?.let(::Messenger)
                        if (serviceMessenger == null) {
                            waiter.completeExceptionally(IllegalStateException("Layanan AI tidak tersedia"))
                            return
                        }
                        messenger = serviceMessenger
                        connectWaiter = null
                        waiter.complete(serviceMessenger)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        onDisconnected("Proses AI dihentikan oleh sistem")
                    }

                    override fun onBindingDied(name: ComponentName?) {
                        onDisconnected("Proses AI perlu dimuat ulang")
                    }

                    override fun onNullBinding(name: ComponentName?) {
                        onDisconnected("Layanan AI menolak binding")
                    }
                }
                binding = connection
                val ok = context.bindService(
                    Intent(context, AiInferenceService::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
                if (!ok) {
                    binding = null
                    waiter.completeExceptionally(IllegalStateException("Proses AI tidak dapat dimulai"))
                }
            }
            try {
                withTimeout(15_000) { waiter.await() }
            } catch (t: Throwable) {
                if (!waiter.isCompleted) waiter.completeExceptionally(t)
                disconnect()
                throw t
            }
        }
    }

    private suspend fun request(
        service: Messenger,
        what: Int,
        payload: Bundle,
        timeoutMs: Long,
    ): Bundle {
        val id = requestIds.getAndIncrement()
        val waiter = CompletableDeferred<Bundle>()
        pending[id] = waiter
        payload.putInt(AiProtocol.KEY_REQUEST_ID, id)
        return try {
            service.send(
                Message.obtain(null, what).apply {
                    data = payload
                    replyTo = replyMessenger
                },
            )
            withTimeout(timeoutMs) { waiter.await() }
        } catch (t: Throwable) {
            pending.remove(id)
            if (t.message?.contains("binder", ignoreCase = true) == true) disconnect()
            throw t
        }
    }

    override suspend fun unload() {
        val current = messenger
        if (current != null) {
            runCatching {
                request(
                    current,
                    AiProtocol.MSG_UNLOAD,
                    Bundle(),
                    timeoutMs = 15_000,
                )
            }
        }
        disconnect()
        _status.value = AiEngineStatus()
    }

    private fun onDisconnected(message: String) {
        _status.value = AiEngineStatus(AiEngineState.ERROR, message)
        val failure = IllegalStateException(message)
        pending.values.forEach { it.completeExceptionally(failure) }
        pending.clear()
        connectWaiter?.completeExceptionally(failure)
        connectWaiter = null
        messenger = null
        binding = null
    }

    private fun disconnect() {
        val current = binding
        if (current != null) {
            runCatching { context.unbindService(current) }
        }
        binding = null
        messenger = null
        connectWaiter = null
        val failure = IllegalStateException("Koneksi AI ditutup")
        pending.values.forEach { it.completeExceptionally(failure) }
        pending.clear()
    }
}
