package com.catetin.app.ingestion.gmail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.catetin.app.BuildConfig
import com.catetin.app.data.db.GmailSyncEntity
import com.catetin.app.data.repo.FinanceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse

@Singleton
class GmailAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: KeystoreTokenStore,
    private val repository: FinanceRepository,
) {
    @Volatile private var pendingRequest: AuthorizationRequest? = null

    fun isConfigured(): Boolean = BuildConfig.GMAIL_OAUTH_CLIENT_ID.isNotBlank()

    fun isConnected(): Boolean = tokenStore.load() != null

    fun createSignInIntent(): Intent? {
        if (!isConfigured()) return null
        val request = AuthorizationRequest.Builder(
            authorizationServiceConfiguration(),
            BuildConfig.GMAIL_OAUTH_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse("${BuildConfig.GMAIL_REDIRECT_SCHEME}:/oauth2redirect"),
        )
            .setScopes(GMAIL_READONLY_SCOPE)
            .setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier())
            .setAdditionalParameters(mapOf("access_type" to "offline"))
            .setPromptValues("consent")
            .build()
        pendingRequest = request
        return AuthorizationService(context).getAuthorizationRequestIntent(request)
    }

    suspend fun completeSignIn(result: Intent) {
        val request = pendingRequest ?: error("Permintaan OAuth tidak ditemukan")
        AuthorizationException.fromIntent(result)?.let { throw it }
        val response = AuthorizationResponse.fromIntent(result) ?: error("Respons izin Google tidak valid")
        require(response.request.state == request.state) { "State OAuth tidak cocok" }

        val service = AuthorizationService(context)
        try {
            val tokenResponse = suspendCancellableCoroutine<TokenResponse> { continuation ->
                service.performTokenRequest(response.createTokenExchangeRequest()) { token, exception ->
                    if (exception != null) continuation.resumeWithException(exception)
                    else if (token == null) continuation.resumeWithException(IllegalStateException("Token OAuth kosong"))
                    else continuation.resume(token)
                }
            }
            val state = AuthState(response, null)
            state.update(tokenResponse, null)
            require(!tokenResponse.refreshToken.isNullOrBlank()) {
                "Google tidak memberikan refresh token. Cabut akses Catet.in di akun Google lalu hubungkan ulang."
            }
            tokenStore.save(state.jsonSerializeString())
            pendingRequest = null
            val previous = repository.getGmailSync()
            repository.saveGmailSync(
                (previous ?: GmailSyncEntity()).copy(
                    status = "SYNCING",
                    message = if (previous?.lastHistoryId.isNullOrBlank() && previous?.backfillAnchorAt == null) {
                        "Menunggu sinkronisasi pertama"
                    } else {
                        "Melanjutkan sinkronisasi Gmail…"
                    },
                ),
            )
        } finally {
            service.dispose()
        }
    }

    suspend fun freshAccessToken(forceRefresh: Boolean = false): String {
        val serialized = tokenStore.load()
            ?: throw GmailReconnectRequiredException("Gmail belum terhubung")
        val state = runCatching { AuthState.jsonDeserialize(serialized) }
            .getOrElse { error ->
                tokenStore.clear()
                markReconnectNeeded("Sesi Gmail tidak dapat dibuka")
                throw GmailReconnectRequiredException("Sesi Gmail tidak dapat dibuka", error)
            }
        val refresh = state.refreshToken
        if (refresh.isNullOrBlank()) {
            tokenStore.clear()
            markReconnectNeeded("Refresh token Gmail tidak tersedia")
            throw GmailReconnectRequiredException("Refresh token Gmail tidak tersedia")
        }
        if (!forceRefresh && !state.needsTokenRefresh && !state.accessToken.isNullOrBlank()) {
            return state.accessToken!!
        }

        val service = AuthorizationService(context)
        return try {
            suspendCancellableCoroutine { continuation ->
                state.performActionWithFreshTokens(service) { accessToken, _, exception ->
                    if (exception != null) {
                        continuation.resumeWithException(exception)
                    } else if (accessToken.isNullOrBlank()) {
                        continuation.resumeWithException(
                            GmailReconnectRequiredException("Token akses Gmail tidak tersedia"),
                        )
                    } else {
                        continuation.resume(accessToken)
                    }
                }
            }.also { tokenStore.save(state.jsonSerializeString()) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            when {
                t is GmailReconnectRequiredException -> {
                    tokenStore.clear()
                    markReconnectNeeded(t.message ?: "Hubungkan ulang Gmail")
                    throw t
                }
                t is AuthorizationException && requiresGmailReconnect(t.type, t.code, t.error) -> {
                    tokenStore.clear()
                    markReconnectNeeded("Hubungkan ulang Gmail untuk melanjutkan sinkronisasi")
                    throw GmailReconnectRequiredException("Sesi Gmail perlu diperbarui", t)
                }
                t is IllegalStateException -> {
                    tokenStore.clear()
                    markReconnectNeeded("Hubungkan ulang Gmail untuk melanjutkan sinkronisasi")
                    throw GmailReconnectRequiredException("Sesi Gmail perlu diperbarui", t)
                }
                else -> {
                    // A network/server failure while refreshing is retryable. Do not discard a
                    // still-valid refresh token or force the user through the consent flow.
                    throw GmailTransientException("Tidak dapat memperbarui akses Gmail", t)
                }
            }
        } finally {
            service.dispose()
        }
    }

    suspend fun disconnect() {
        tokenStore.clear()
        pendingRequest = null
        repository.saveGmailSync(
            GmailSyncEntity(status = "NOT_CONNECTED", message = "Koneksi Gmail diputuskan"),
        )
    }

    private suspend fun markReconnectNeeded(message: String) {
        val current = repository.getGmailSync() ?: GmailSyncEntity()
        repository.saveGmailSync(current.markReconnectRequired(message))
    }

    @VisibleForTesting
    fun authorizationServiceConfiguration() = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token"),
    )
}

/** True only for auth failures that a retry cannot fix without a new consent flow. */
internal fun requiresGmailReconnect(type: Int, code: Int, error: String?): Boolean {
    if (type != AuthorizationException.TYPE_GENERAL_ERROR) return true
    if (code == AuthorizationException.GeneralErrors.NETWORK_ERROR.code) return false
    if (code == AuthorizationException.GeneralErrors.SERVER_ERROR.code) return false
    return error != "temporarily_unavailable"
}
