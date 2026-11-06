import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The signing key is never in the repo. Its SHA-1 is what you register in Google Cloud Console.
//  - Cloud build: GitHub secrets, passed in as HUSHSYNC_KEYSTORE / HUSHSYNC_KEYSTORE_PASSWORD.
//  - Local build: ../keys/hushsync-keystore.properties (outside the project folder).
// With neither, builds fall back to your own Android debug key (register that SHA-1 instead).
val keyProps = Properties().apply {
    val envFile = System.getenv("HUSHSYNC_KEYSTORE")
    val envPassword = System.getenv("HUSHSYNC_KEYSTORE_PASSWORD")
    val localFile = rootProject.file("../keys/hushsync-keystore.properties")
    if (!envFile.isNullOrBlank() && !envPassword.isNullOrBlank()) {
        setProperty("storeFile", envFile)
        setProperty("storePassword", envPassword)
        setProperty("keyPassword", envPassword)
        setProperty("keyAlias", System.getenv("HUSHSYNC_KEY_ALIAS") ?: "hushsync")
    } else if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val hasSigningKey = keyProps.getProperty("storeFile") != null

android {
    namespace = "com.hushsync.app"
    compileSdk = 35

    defaultConfig {
        // Must match the package name you enter in Google Cloud Console.
        applicationId = "com.hushsync.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasSigningKey) {
            create("hushsync") {
                storeFile = file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug and release are signed with the same key, so the SHA-1 never changes.
        debug {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("hushsync")
        }
        release {
            isMinifyEnabled = false
            if (hasSigningKey) signingConfig = signingConfigs.getByName("hushsync")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
