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
        versionCode = 35
        versionName = "0.31.5-dosbox-native-entry"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs.useLegacyPackaging = true }
}
