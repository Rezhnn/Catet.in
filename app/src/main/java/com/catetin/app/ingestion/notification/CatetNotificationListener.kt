package com.catetin.app.ingestion.notification

import android.app.Notification
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.catetin.app.data.repo.FinanceRepository
import com.catetin.parser.model.RawArtifact
import com.catetin.parser.model.SourceType
import com.catetin.app.widget.CatetWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CatetNotificationListener : NotificationListenerService() {
    @Inject lateinit var repository: FinanceRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = Channel<StatusBarNotification>(capacity = 48)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeIngested = false

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            for (notification in pending) {
                processSafely(notification)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationAccess.setConnected(true)
        if (!activeIngested) {
            activeIngested = true
            serviceScope.launch {
                runCatching { activeNotifications.orEmpty().forEach { pending.trySend(it) } }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        // Only the package identifier is inspected on the binder thread. The
        // notification extras are not read until the allow-list check passes.
        pending.trySend(sbn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationAccess.setConnected(false)
        mainHandler.postDelayed({
            if (NotificationAccess.isGranted(this)) {
                runCatching {
                    requestRebind(ComponentName(this, CatetNotificationListener::class.java))
                }
            }
        }, 1_500)
    }

    private suspend fun processSafely(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return
        if (!repository.isNotificationPackageAllowed(packageName)) return
        runCatching {
            val text = readAllowedText(sbn)
            if (text.isBlank()) return@runCatching
            val outcome = repository.ingest(
                RawArtifact(
                    sourceType = SourceType.NOTIFICATION,
                    sourceApp = packageName,
                    text = text,
                    timestampMillis = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    title = sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                    externalId = "${sbn.key}:${sbn.postTime}",
                ),
            )
            if (outcome.transactionId != null) CatetWidgetProvider.refresh(this@CatetNotificationListener)
        }
    }

    private fun readAllowedText(sbn: StatusBarNotification): String {
        val extras = sbn.notification?.extras ?: Bundle.EMPTY
        val parts = linkedSetOf<String>()

        fun add(value: CharSequence?) {
            value?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        }

        add(extras.getCharSequence(Notification.EXTRA_TITLE))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach(::add)
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { message ->
            if (message is Bundle) {
                add(message.getCharSequence(Notification.EXTRA_TEXT))
            }
        }
        return parts.joinToString("\n").take(12_000)
    }

    override fun onDestroy() {
        NotificationAccess.setConnected(false)
        mainHandler.removeCallbacksAndMessages(null)
        pending.close()
        serviceScope.cancel()
        super.onDestroy()
    }
}
