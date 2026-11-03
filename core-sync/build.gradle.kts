// Wspólne DTO i Wearable Data Layer (telefon <-> zegarek).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zacco.rytm.sync"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdkPhone.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.play.services.wearable)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.play.services)
}
