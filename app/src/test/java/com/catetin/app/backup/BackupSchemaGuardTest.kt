package com.catetin.app.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSchemaGuardTest {
    @Test
    fun `empty backup uses safe defaults`() {
        val file = Json { ignoreUnknownKeys = true }.decodeFromString(BackupFile.serializer(), "{}")
        assertEquals("catetin-backup", file.format)
        assertEquals(1, file.version)
        assertTrue(file.transactions.isEmpty())
        assertEquals("IDR", file.settings.defaultCurrency)
    }

    @Test
    fun `backup excludes credentials by schema`() {
        val source = java.io.File("src/main/java/com/catetin/app/backup/BackupModels.kt").readText()
        assertTrue(!source.contains("accessToken", ignoreCase = true))
        assertTrue(!source.contains("refreshToken", ignoreCase = true))
        assertTrue(!source.contains("clientSecret", ignoreCase = true))
    }
}
