// Room, repozytoria, Health Connect, synchronizacja. W fazie 0: odczyt i zrzut danych z Health Connect.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.zacco.rytm.data"
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
    api(project(":core-domain"))
    api(libs.health.connect.client)
    implementation(libs.kotlinx.coroutines.android)
}
