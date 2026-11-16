package com.catetin.app.ai.lifecycle

import com.catetin.app.ai.engine.AiEngineState
import com.catetin.app.ai.engine.AiEngineStatus
import com.catetin.app.ai.engine.LocalLlmEngine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the lazy-load policy. It is inert until the Assistant screen asks for
 * a model and always returns the :ai process after a short grace period.
 */
@Singleton
class AiLifecycleCoordinator @Inject constructor(
    private val engine: LocalLlmEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var loadJob: Job? = null
    private var unloadJob: Job? = null
    @Volatile private var targetPath: String? = null

    val status: StateFlow<AiEngineStatus> = engine.status

    fun onAssistantVisible(modelPath: String?) {
        val path = modelPath?.takeIf { File(it).isFile && File(it).length() > 10L * 1024 * 1024 }
            ?: return
        unloadJob?.cancel()
        unloadJob = null
        if (engine.status.value.state == AiEngineState.READY && targetPath == path) return
        if (loadJob?.isActive == true && targetPath == path) return

        targetPath = path
        loadJob?.cancel()
        loadJob = scope.launch {
            mutex.withLock {
                if (engine.status.value.state == AiEngineState.READY && targetPath == path) return@withLock
                runCatching { engine.load(path) }
            }
        }
    }

    fun onAssistantHidden(graceMillis: Long = 15_000) {
        scheduleUnload(graceMillis)
    }

    fun onAppBackgrounded() {
        scheduleUnload(0)
    }

    fun onTrimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            scheduleUnload(0)
        }
    }

    private fun scheduleUnload(graceMillis: Long) {
        if (engine.status.value.state == AiEngineState.UNLOADED) return
        unloadJob?.cancel()
        unloadJob = scope.launch {
            if (graceMillis > 0) delay(graceMillis)
            val pendingLoad = loadJob
            if (pendingLoad?.isActive == true) pendingLoad.join()
            mutex.withLock {
                runCatching { engine.unload() }
                targetPath = null
            }
        }
    }
}
