// Wspólne DTO i Data Layer (telefon <-> zegarek). Rozbudowa w fazie 2.
plugins {
    alias(libs.plugins.android.library)
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
