import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Server address and shared key come from secrets.properties (never committed).
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.nave.screenreview"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nave.screenreview"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SERVER_URL", "\"${secrets.getProperty("SERVER_URL", "")}\"")
        buildConfigField("String", "APP_KEY", "\"${secrets.getProperty("APP_KEY", "")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(secrets.getProperty("KEYSTORE", "release.jks"))
            storePassword = secrets.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = secrets.getProperty("KEY_ALIAS", "review")
            keyPassword = secrets.getProperty("KEYSTORE_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
