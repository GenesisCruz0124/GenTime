import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Read Supabase config from local.properties (dev) or environment (CI).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String, env: String): String =
    (localProps.getProperty(key) ?: System.getenv(env) ?: "").let { "\"$it\"" }

android {
    namespace = "dev.gentime.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.gentime.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", cfg("SUPABASE_URL", "SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", cfg("SUPABASE_ANON_KEY", "SUPABASE_ANON_KEY"))
        buildConfigField("String", "APP_VERSION", "\"1.0.0\"")
    }

    signingConfigs {
        create("release") {
            // CI provides these via env (decoded keystore + secrets).
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Pin transitive AndroidX Browser down: 1.10.0 (pulled by supabase auth-kt
    // for OAuth Custom Tabs, which this app doesn't use) requires compileSdk 36
    // / AGP 8.9+. Hold it at a compileSdk-35-compatible version.
    constraints {
        implementation("androidx.browser:browser") {
            version { strictly("1.8.0") }
            because("browser 1.10.0 requires compileSdk 36 / AGP 8.9+; not needed here")
        }
    }

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Biometrics
    implementation("androidx.biometric:biometric:1.1.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Room (offline queue)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (sync)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Encrypted prefs (device_id, session)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Supabase (Kotlin) — 3.x line: modules are auth-kt / postgrest-kt / realtime-kt
    // under package io.github.jan.supabase.*. Pinned to 3.0.3, which is built
    // with Kotlin 2.0.x (newer 3.6.x pulls ktor 3.4 / Kotlin 2.3 metadata that
    // the project's Kotlin 2.0.20 compiler can't read).
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")

    // FCM
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
