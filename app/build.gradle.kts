import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingValue(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: keystoreProperties.getProperty(name)
        ?: localProperties.getProperty(name)

val releaseStorePath = signingValue("CATETIN_KEYSTORE_PATH")?.trim().orEmpty()
val releaseStorePassword = signingValue("CATETIN_KEYSTORE_PASSWORD")?.trim().orEmpty()
val releaseKeyAlias = signingValue("CATETIN_KEY_ALIAS")?.trim().orEmpty()
val releaseKeyPassword = signingValue("CATETIN_KEY_PASSWORD")?.trim().orEmpty()
val releaseStoreFile = releaseStorePath.takeIf { it.isNotBlank() }?.let(rootProject::file)
val releaseSigningReady = releaseStoreFile?.isFile == true &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()

val gmailClientId = providers.gradleProperty("GMAIL_OAUTH_CLIENT_ID").orNull
    ?: providers.environmentVariable("GMAIL_OAUTH_CLIENT_ID").orNull
    ?: localProperties.getProperty("GMAIL_OAUTH_CLIENT_ID", "")
val gmailRedirectScheme = if (gmailClientId.isBlank()) {
    "com.catetin.app"
} else {
    "com.googleusercontent.apps." + gmailClientId.substringBefore(".apps.googleusercontent.com")
}
val escapedClientId = gmailClientId.replace("\\", "\\\\").replace("\"", "\\\"")
val escapedRedirectScheme = gmailRedirectScheme.replace("\"", "\\\"")

android {
    namespace = "com.catetin.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.catetin.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GMAIL_OAUTH_CLIENT_ID", "\"$escapedClientId\"")
        buildConfigField("String", "GMAIL_REDIRECT_SCHEME", "\"$escapedRedirectScheme\"")
        manifestPlaceholders["gmailRedirectScheme"] = gmailRedirectScheme
        manifestPlaceholders["appAuthRedirectScheme"] = gmailRedirectScheme
        buildConfigField(
            "String",
            "MODEL_DOWNLOAD_URL",
            "\"https://huggingface.co/ggml-org/Qwen3.5-0.8B-GGUF/resolve/9447f74101aeb4e93621884dfa36ee8effb8831b/Qwen3.5-0.8B-Q4_0.gguf?download=true\"",
        )
        buildConfigField("String", "MODEL_SHA256", "\"57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf\"")
        buildConfigField("long", "MODEL_SIZE_BYTES", "563036064L")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }

    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":parser-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.biometric)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.appauth)
    implementation(libs.llama.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
