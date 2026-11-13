plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.utku.perde"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.utku.perde"
        minSdk = 26
        targetSdk = 34
        versionCode = runNumber
        versionName = "0.2.$runNumber"
    }

    signingConfigs {
        // Fixed key kept in the repo on purpose, so every build can update the
        // previous one in place (self-update depends on this).
        create("release") {
            storeFile = file("perde.jks")
            storePassword = "perde-launcher"
            keyAlias = "perde"
            keyPassword = "perde-launcher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
