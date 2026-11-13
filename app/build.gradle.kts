plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Single source of truth for the version: /VERSION (x.y.z). versionCode = x*10000 + y*100 + z.
val appVersion: String = rootProject.file("VERSION").readText().trim()
val appVersionCode: Int = appVersion.split(".").let { (major, minor, patch) ->
    major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
}

android {
    namespace = "de.c3.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.c3.tv"
        minSdk = 30 // Fire OS 8 = Android 11
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        // DEV ONLY: committed on purpose so CI builds can be updated in place
        // with `adb install -r`. Replace with a private key before any public release.
        create("dev") {
            storeFile = file("dev.keystore")
            storePassword = "c3devstore"
            keyAlias = "c3dev"
            keyPassword = "c3devstore"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("dev")
        }
        debug {
            signingConfig = signingConfigs.getByName("dev")
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
        buildConfig = false
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// No library dependencies on purpose: the app uses only the Android framework
// (plus the Kotlin standard library added by the Kotlin plugin).
