plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.spysim"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.spysim"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // The simulator's ECJ backend rejects the Java 17 target flag (-17).
    // Use Java 8 bytecode for broad compatibility with this build environment.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}
