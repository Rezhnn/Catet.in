package com.catetin.app

import android.os.Bundle
import android.view.WindowManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.catetin.app.ai.lifecycle.AiLifecycleCoordinator
import com.catetin.app.data.prefs.AppSettings
import com.catetin.app.data.prefs.SettingsRepository
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import com.catetin.app.ui.AppLockScreen
import com.catetin.app.ui.CatetApp
import com.catetin.app.widget.CatetWidgetProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var aiLifecycle: AiLifecycleCoordinator
    @Inject lateinit var settingsRepository: SettingsRepository

    private val locked = MutableStateFlow(false)
    private val lockError = MutableStateFlow<String?>(null)
    private var lastBackgroundAt = 0L
    private var lastUnlockedAt = 0L
    private var authInProgress = false
    private var currentSettings: AppSettings? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            settingsRepository.settings.collect { settings ->
                val wasEnabled = currentSettings?.appLockEnabled == true
                currentSettings = settings
                updateSecureFlag(settings.keepScreenSecure)
                if (settings.appLockEnabled && !wasEnabled && lastUnlockedAt > 0) {
                    locked.value = true
                }
                if (!settings.appLockEnabled) {
                    locked.value = false
                    lockError.value = null
                }
            }
        }
        setContent {
            val isLocked by locked.collectAsState()
            val error by lockError.collectAsState()
            if (isLocked) {
                AppLockScreen(onUnlock = ::requestUnlock, error = error)
            } else {
                CatetApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CatetWidgetProvider.refresh(this)
        if (authInProgress) return
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            currentSettings = settings
            updateSecureFlag(settings.keepScreenSecure)
            if (!settings.appLockEnabled) {
                locked.value = false
                return@launch
            }
            val timeout = settings.lockTimeoutSeconds.coerceAtLeast(0) * 1_000L
            val reference = if (lastBackgroundAt > 0) lastBackgroundAt else 0L
            val needsUnlock = lastUnlockedAt == 0L || System.currentTimeMillis() - reference >= timeout
            if (needsUnlock) {
                locked.value = true
                requestUnlock()
            }
        }
    }

    override fun onStop() {
        lastBackgroundAt = System.currentTimeMillis()
        aiLifecycle.onAppBackgrounded()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        aiLifecycle.onTrimMemory(level)
        super.onTrimMemory(level)
    }

    private fun requestUnlock() {
        if (authInProgress) return
        val manager = BiometricManager.from(this)
        val supportsDeviceCredential = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val allowed = if (supportsDeviceCredential) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        val canAuth = manager.canAuthenticate(allowed)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            lockError.value = "Biometrik/PIN perangkat belum tersedia. Buka kunci perangkat, lalu coba lagi."
            locked.value = true
            return
        }
        authInProgress = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    lastUnlockedAt = System.currentTimeMillis()
                    lockError.value = null
                    locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authInProgress = false
                    lockError.value = "Catet.in tetap terkunci. ${errString.toString().take(140)}"
                    locked.value = true
                }

                override fun onAuthenticationFailed() {
                    lockError.value = "Tidak cocok. Coba lagi."
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka Catet.in")
            .setSubtitle("Data keuanganmu tersimpan secara lokal")
        if (supportsDeviceCredential) info.setAllowedAuthenticators(allowed)
        else {
            @Suppress("DEPRECATION")
            info.setDeviceCredentialAllowed(true)
        }
        prompt.authenticate(info.build())
    }

    private fun updateSecureFlag(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
