import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Klient LLM, prompty, parsowanie JSON, limity kosztów (faza 4). Czysty Kotlin, bez Androida.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core-domain"))
}
