plugins {
    id("com.android.application")
}
android {
    namespace = "org.winkit.android.a31smoke"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.winkit.android.a31smoke"
        minSdk = 28
        targetSdk = 35
        versionCode = 37
        versionName = "0.31.7-android-sdl-video"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs.useLegacyPackaging = true }
}
