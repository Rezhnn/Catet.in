package com.catetin.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureGuardTest {
    private fun manifest(): String = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `manifest has no forbidden sensitive permissions`() {
        val text = manifest()
        listOf(
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
        ).forEach { permission ->
            assertFalse("Forbidden permission present: $permission", text.contains(permission))
        }
    }

    @Test
    fun `ai inference service stays in child process`() {
        val text = manifest()
        val service = text.substringAfter("android:name=\".ai.service.AiInferenceService\"")
            .substringBefore("/>")
        assertTrue(service.contains("android:process=\":ai\""))
        assertFalse(service.contains("isolatedProcess"))
    }

    @Test
    fun `parser core does not depend on app or ai packages`() {
        val root = File("../parser-core/src")
        val imports = root.walkTopDown().filter { it.extension == "kt" }
            .flatMap { it.readLines().asSequence() }
            .filter { it.startsWith("import ") }
            .toList()
        assertFalse(imports.any { it.contains("com.catetin.app") })
        assertFalse(imports.any { it.contains(".ai.") })
    }

    @Test
    fun `data and domain packages do not import ai engine`() {
        val roots = listOf(
            File("src/main/java/com/catetin/app/data"),
            File("src/main/java/com/catetin/app/domain"),
        )
        roots.flatMap { it.walkTopDown().filter { f -> f.extension == "kt" }.toList() }.forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} imports AI engine", text.contains("com.catetin.app.ai.engine"))
            assertFalse("${file.name} imports llama", text.contains("dev.ffmpegkit.llama"))
        }
    }

    @Test
    fun `no analytics crash or advertising sdk is declared`() {
        val catalog = File("../gradle/libs.versions.toml").readText().lowercase()
        listOf("firebase", "crashlytics", "sentry", "datadog", "applovin", "admob").forEach {
            assertFalse("Telemetry/ad SDK found: $it", catalog.contains(it))
        }
    }
}
