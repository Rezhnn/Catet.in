package com.catetin.app.ingestion.gmail

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailApiClientRetryTest {

    @Test
    fun `cancellation inside a request is never retried`() = runTest {
        var attempts = 0
        var delays = 0

        val failure = runCatching {
            withGmailRetry(delayFor = { delays++ }) {
                attempts++
                throw CancellationException("dibatalkan")
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(1, attempts)
        assertEquals(0, delays)
    }

    @Test
    fun `cancellation during backoff is not converted into another retry`() = runTest {
        var attempts = 0

        val failure = runCatching {
            withGmailRetry(
                delayFor = { throw CancellationException("dibatalkan saat jeda") },
            ) {
                attempts++
                throw GmailApiException("sibuk", 503)
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(1, attempts)
    }

    @Test
    fun `transient server failures retry with backoff`() = runTest {
        var attempts = 0
        var delays = 0

        val result = withGmailRetry(delayFor = { delays++ }) {
            attempts++
            if (attempts < 3) throw GmailApiException("sibuk", 500) else "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, attempts)
        assertEquals(2, delays)
    }

    @Test
    fun `network io failures retry`() = runTest {
        var attempts = 0

        val result = withGmailRetry(delayFor = {}) {
            attempts++
            if (attempts == 1) throw IOException("putus") else "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `permanent http errors are not retried`() = runTest {
        var attempts = 0

        val failure = runCatching {
            withGmailRetry(delayFor = {}) {
                attempts++
                throw GmailApiException("tidak ditemukan", 404)
            }
        }.exceptionOrNull()

        assertTrue(failure is GmailApiException)
        assertEquals(404, (failure as GmailApiException).statusCode)
        assertEquals(1, attempts)
    }

    @Test
    fun `only transient status codes default to retryable`() {
        assertFalse(GmailApiException("hilang", 404).retryable)
        assertFalse(GmailApiException("ditolak", 400).retryable)
        assertTrue(GmailApiException("batas", 429).retryable)
        assertTrue(GmailApiException("server", 503).retryable)
    }
}
