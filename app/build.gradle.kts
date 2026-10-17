plugins {
    id("com.android.application")
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

    // Java 8 bytecode target for compatibility with the simulator build environment.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
