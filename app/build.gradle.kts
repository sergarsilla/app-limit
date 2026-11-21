import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.applimit"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.applimit"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    // Signing comes from an untracked keystore.properties or the environment; without either,
    // release builds are produced unsigned.
    val signing = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

    fun setting(key: String, variable: String): String? =
        System.getenv(variable) ?: signing.getProperty(key)

    signingConfigs {
        create("release") {
            setting("storeFile", "KEYSTORE_FILE")?.let { storeFile = file(it) }
            storePassword = setting("storePassword", "KEYSTORE_PASSWORD")
            keyAlias = setting("keyAlias", "KEY_ALIAS")
            keyPassword = setting("keyPassword", "KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile?.exists() == true }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // No dependency metadata blob in the APK: it is only read by Google Play.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
