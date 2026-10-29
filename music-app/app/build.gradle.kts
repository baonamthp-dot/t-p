plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val openAiBackendUrl = (project.findProperty("OPENAI_BACKEND_URL") as String?) ?: System.getenv("OPENAI_BACKEND_URL") ?: ""

android {
    namespace = "com.example.musicapp"
    compileSdk = 35
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.example.musicapp"
        buildConfigField("String", "OPENAI_BACKEND_URL", "\"$openAiBackendUrl\"")
        minSdk = 23
        targetSdk = 35
        versionCode = 8
        versionName = "1.7"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        debug {
            // Shrink bytecode and unused resources to reduce the installable APK size.
            // R8 keeps manifest-declared Android components and optimizes the remaining code.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
