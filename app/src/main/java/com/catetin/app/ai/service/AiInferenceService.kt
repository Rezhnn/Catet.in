package com.catetin.app.ai.service

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only class in Catet.in that touches llama.cpp. The manifest places this
 * service in a dedicated :ai process, so an OOM here never takes down the
 * notification listener. It deliberately has no dependency on Room or Hilt.
 */
class AiInferenceService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineMutex = Mutex()
    private var model: LlamaModel? = null
    private var loadedPath: String? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                AiProtocol.MSG_LOAD -> handleLoad(msg)
                AiProtocol.MSG_GENERATE -> handleGenerate(msg)
                AiProtocol.MSG_UNLOAD -> handleUnload(msg)
                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handleLoad(msg: Message) {
        val reply = msg.replyTo
        val requestId = msg.data.getInt(AiProtocol.KEY_REQUEST_ID)
        val path = msg.data.getString(AiProtocol.KEY_MODEL_PATH)
        serviceScope.launch {
            try {
                require(!path.isNullOrBlank()) { "Lokasi model kosong" }
                val file = File(path)
                require(file.isFile && file.length() > 10L * 1024 * 1024) { "Berkas model tidak ditemukan" }

                engineMutex.withLock {
                    if (model?.isLoaded == true && loadedPath == path) return@withLock
                    releaseLocked()
                    val threads = max(2, min(4, Runtime.getRuntime().availableProcessors() / 2))
                    model = Llama.loadModel(
                        path,
                        LlamaConfig(
                            contextSize = 2048,
                            threads = threads,
                            gpuLayers = 0,
                            temperature = 0.1f,
                            topP = 0.9f,
                            topK = 40,
                            seed = 42,
                        ),
                    )
                    loadedPath = path
                }
                reply(reply, requestId, ok = true, state = "READY")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                releaseEngine()
                reply(reply, requestId, ok = false, error = loadErrorMessage(t))
            }
        }
    }

    private fun handleGenerate(msg: Message) {
        val reply = msg.replyTo
        val requestId = msg.data.getInt(AiProtocol.KEY_REQUEST_ID)
        val prompt = msg.data.getString(AiProtocol.KEY_PROMPT)
        val systemPrompt = msg.data.getString(AiProtocol.KEY_SYSTEM_PROMPT).orEmpty()
        val maxTokens = msg.data.getInt(AiProtocol.KEY_MAX_TOKENS, 160).coerceIn(32, 256)
        serviceScope.launch {
            try {
                require(!prompt.isNullOrBlank()) { "Prompt kosong" }
                val text = engineMutex.withLock {
                    val active = model?.takeIf { it.isLoaded } ?: error("Model belum dimuat")
                    Llama.complete(active, prompt, systemPrompt, maxTokens).text
                }
                reply(reply, requestId, ok = true, text = text, state = "READY")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                reply(reply, requestId, ok = false, error = t.message ?: "Inferensi gagal")
            }
        }
    }

    private fun handleUnload(msg: Message) {
        val reply = msg.replyTo
        val requestId = msg.data.getInt(AiProtocol.KEY_REQUEST_ID)
        serviceScope.launch {
            releaseEngine()
            reply(reply, requestId, ok = true, state = "UNLOADED")
        }
    }

    private fun reply(
        replyTo: Messenger?,
        requestId: Int,
        ok: Boolean,
        text: String? = null,
        error: String? = null,
        state: String? = null,
    ) {
        val data = Bundle().apply {
            putInt(AiProtocol.KEY_REQUEST_ID, requestId)
            putBoolean(AiProtocol.KEY_OK, ok)
            text?.let { putString(AiProtocol.KEY_TEXT, it) }
            error?.let { putString(AiProtocol.KEY_ERROR, it) }
            state?.let { putString(AiProtocol.KEY_STATE, it) }
        }
        runCatching { replyTo?.send(Message.obtain(null, AiProtocol.MSG_REPLY).apply { this.data = data }) }
    }

    private suspend fun releaseEngine() {
        engineMutex.withLock { releaseLocked() }
    }

    private fun releaseLocked() {
        model?.let { runCatching { Llama.releaseModel(it) } }
        model = null
        loadedPath = null
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            serviceScope.launch { releaseEngine() }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        serviceScope.launch { releaseEngine() }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch {
            releaseEngine()
            Process.killProcess(Process.myPid())
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        serviceScope.launch {
            releaseEngine()
            stopSelf()
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { releaseLocked() }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun loadErrorMessage(t: Throwable): String = when {
        t.message?.contains("Failed to load", ignoreCase = true) == true ->
            "Model gagal dimuat. Pastikan berkas GGUF Qwen3.5-0.8B Q4 tidak rusak dan memori perangkat cukup."
        else -> t.message?.take(240) ?: "Model gagal dimuat"
    }
}
