package com.catetin.app.ingestion.gmail

import java.io.File
import net.openid.appauth.AuthorizationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailAuthPolicyTest {

    @Test
    fun `gmail requests exactly the readonly scope`() {
        assertEquals("https://www.googleapis.com/auth/gmail.readonly", GMAIL_READONLY_SCOPE)
        val source = File("src/main/java/com/catetin/app/ingestion/gmail/GmailAuthManager.kt").readText()
        assertTrue(source.contains(".setScopes(GMAIL_READONLY_SCOPE)"))
        assertFalse(source.contains("gmail.modify"))
        assertFalse(source.contains("gmail.compose"))
        assertFalse(source.contains("gmail.send"))
    }

    @Test
    fun `network and server failures during token refresh do not force reconnect`() {
        assertFalse(
            requiresGmailReconnect(
                AuthorizationException.TYPE_GENERAL_ERROR,
                AuthorizationException.GeneralErrors.NETWORK_ERROR.code,
                null,
            ),
        )
        assertFalse(
            requiresGmailReconnect(
                AuthorizationException.TYPE_GENERAL_ERROR,
                AuthorizationException.GeneralErrors.SERVER_ERROR.code,
                null,
            ),
        )
    }

    @Test
    fun `invalid grant during token refresh requires reconnect`() {
        assertTrue(
            requiresGmailReconnect(
                AuthorizationException.TYPE_OAUTH_TOKEN_ERROR,
                AuthorizationException.TokenRequestErrors.INVALID_GRANT.code,
                "invalid_grant",
            ),
        )
    }
}
