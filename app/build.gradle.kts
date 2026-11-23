import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun readConfig(propKey: String, envKey: String): String {
    return (providers.gradleProperty(propKey).orNull
        ?: localProperties.getProperty(propKey)
        ?: System.getenv(envKey)
        ?: "").trim()
}

fun resolveKeystoreFile(path: String): File {
    val candidate = File(path)
    return if (candidate.isAbsolute) candidate else rootProject.file(path)
}

val supabaseUrl = readConfig("supabase.url", "SUPABASE_URL")
val supabaseKey = readConfig("supabase.key", "SUPABASE_KEY")
if (supabaseUrl.isBlank() || supabaseKey.isBlank()) {
    throw GradleException(
        "Missing supabase.url / SUPABASE_URL or supabase.key / SUPABASE_KEY in local.properties, -P properties, or environment variables."
    )
}

val releaseStoreFile = readConfig("release.storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = readConfig("release.storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = readConfig("release.keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = readConfig("release.keyPassword", "RELEASE_KEY_PASSWORD")

android {
    namespace = "com.example.cloble"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.cloble"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile.isNotBlank()) {
                storeFile = resolveKeystoreFile(releaseStoreFile)
            }
            if (releaseStorePassword.isNotBlank()) {
                storePassword = releaseStorePassword
            }
            if (releaseKeyAlias.isNotBlank()) {
                keyAlias = releaseKeyAlias
            }
            if (releaseKeyPassword.isNotBlank()) {
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isShrinkResources = true
            optimization {
                enable = true
            }
        }
    }
    lint {
        disable += "InvalidFragmentVersionForActivityResult"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

gradle.taskGraph.whenReady {
    val hasReleaseTask = allTasks.any { it.name.contains("Release", ignoreCase = true) }
    if (hasReleaseTask) {
        if (releaseStoreFile.isBlank() ||
            releaseStorePassword.isBlank() ||
            releaseKeyAlias.isBlank() ||
            releaseKeyPassword.isBlank()
        ) {
            throw GradleException(
                "Release signing tidak lengkap. Atur release.storeFile, release.storePassword, release.keyAlias, dan release.keyPassword di local.properties, -P, atau environment variable."
            )
        }
        val ks = resolveKeystoreFile(releaseStoreFile)
        if (!ks.exists()) {
            throw GradleException("File keystore release tidak ditemukan di: ${ks.absolutePath}")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
