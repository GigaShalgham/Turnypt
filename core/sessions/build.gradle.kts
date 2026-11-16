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
    api(project(":core:common"))
    api(project(":core:crypto"))
    api(project(":core:keys"))
    api(project(":core:messages"))
    // v1.3.1: the session manager is the enforcement point of session
    // LIFETIME, so it re-validates timeout values against KeyPolicy here
    // (no cycle: policies depends only on core:common and core:crypto).
    api(project(":security:policies"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
