plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Stego is pure Kotlin, JVM-testable, no Android imports — the plan's
    // §5.1 placement: beside core:crypto, reusing its AEAD/HKDF/Argon2.
    api(project(":core:common"))
    api(project(":core:crypto"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
