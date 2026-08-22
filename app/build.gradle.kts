import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing credentials, from `keystore.properties` at the repo root or, failing that, the
 * environment. Absent means an unsigned release APK, exactly as before this existed: a clean
 * checkout and a fork's CI have no key and must still build.
 */
val releaseKeystore: Map<String, String>? = run {
    val keys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val properties = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }
            ?.inputStream()?.use { load(it) }
    }
    fun value(key: String) =
        (properties.getProperty(key) ?: System.getenv("BLUGAEMAND_" + key.uppercase()))
            ?.takeIf { it.isNotBlank() }

    val entries = keys.mapNotNull { key -> value(key)?.let { key to it } }.toMap()

    // All four or none. A partial set is a typo or a half-set CI secret, and signing with three of
    // them is not a thing that can happen -- saying so beats an APK that quietly comes out unsigned.
    when (entries.size) {
        keys.size -> entries
        0 -> null
        else -> error("Incomplete release signing config; missing ${keys - entries.keys}")
    }
}

android {
    namespace = "com.blugaemand"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blugaemand"
        // BluetoothHidDevice, the API that lets the phone act as an HID peripheral,
        // was added in API 28. There is no supported way to do this below that.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        releaseKeystore?.let { keystore ->
            create("release") {
                // Relative to the repo root, so keystore.properties reads the same on every machine
                // it is copied to; an absolute path passes through unchanged.
                storeFile = rootProject.file(keystore.getValue("storeFile"))
                storePassword = keystore.getValue("storePassword")
                keyAlias = keystore.getValue("keyAlias")
                keyPassword = keystore.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Null when no key is configured, which is what leaves the APK unsigned rather than
            // failing the build.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // For BuildConfig.VERSION_NAME, which the About page shows. Asking PackageManager for it
        // at runtime would read back the same string this file already sets.
        buildConfig = true
    }

    lint {
        abortOnError = true
        // Dependency versions are pinned deliberately to what resolves offline; the "newer version
        // available" family is pure noise here.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
