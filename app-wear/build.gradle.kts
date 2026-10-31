// UI zegarka, kafelki, komplikacje, HRV, trening siłowy. W fazie 0: PoC pomiaru HRV (IBI → RMSSD).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Samsung Health Sensor SDK nie jest w Mavenie: AAR pobiera się ręcznie z developer.samsung.com
// i wrzuca do app-wear/libs/ (plik jest w .gitignore). Bez niego budujemy zaślepkę, żeby CI i reszta projektu działały.
val sensorSdkAars = layout.projectDirectory.dir("libs").asFile
    .listFiles { f -> f.name.startsWith("samsung-health-sensor-api") && f.extension == "aar" }
    .orEmpty()
val hasSensorSdk = sensorSdkAars.isNotEmpty()

android {
    namespace = "com.zacco.rytm.wear"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Ten sam applicationId co telefon: wymaganie Wearable Data Layer API.
        applicationId = "com.zacco.rytm"
        minSdk = libs.versions.minSdkWear.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir(if (hasSensorSdk) "src/samsung/kotlin" else "src/stub/kotlin")
        }
    }
}

dependencies {
    implementation(project(":core-domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    if (hasSensorSdk) {
        implementation(files(sensorSdkAars))
    }
}
