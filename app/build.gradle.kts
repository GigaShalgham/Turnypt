import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing: reads a properties file whose path comes from the
// CK_KEYSTORE_PROPERTIES environment variable (maintainer machines) or
// falls back to keystore.properties next to settings.gradle.kts
// (gitignored). CI has neither, so CI release builds stay unsigned —
// only maintainer-published GitHub Releases carry signed artifacts.
val keystorePropertiesFile: File = System.getenv("CK_KEYSTORE_PROPERTIES")
    ?.let { File(it) }
    ?: rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.turnypt.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.turnypt"
        minSdk = 26
        targetSdk = 36
        versionCode = 34
        versionName = "1.6.0-alpha.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                // v1.5.10: relative storeFile paths resolve against the
                // PROJECT root (rootProject.file), not app/ — the bare
                // "turnypt-release.jks" in keystore.properties used to be
                // read as app/turnypt-release.jks.
                storeFile = rootProject.file(
                    keystoreProperties.getProperty("storeFile")
                )
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // v1.5.10 (lag audit, round two): minification ON. The user
            // reported the app STILL very laggy after v1.5.9 — and the
            // shipped artifact was the DEBUG build: `application-debuggable`
            // (JNI asserts on every call, debugger-attach deopt, no ART AOT
            // optimization) plus 19 dex files of unshrunk Compose. The
            // release variant with R8 + resource shrinking removes all of
            // that; keep rules (proguard-rules.pro) are the conservative
            // set — Compose internals, Hilt/Dagger, Room entities and enum
            // reflection are kept, so runtime behavior is unchanged.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // NOTE: without a signing config, release builds are unsigned.
        }
    }

    buildFeatures {
        compose = true
        // VERSION_NAME is surfaced to the About card via BuildConfig
        // (deep-scan finding 7: the hardcoded "0.1.0" there was stale).
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    // v1.5.10 (lag audit, round two): lintVitalAnalyzeRelease OOM-killed
    // the 4 GiB sandbox daemon mid-assemble (see gradle.properties memory
    // doctrine). Lint analysis is a CI concern, not a packaging step; the
    // release APK gets built first, lint stays runnable on demand.
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:crypto"))
    implementation(project(":core:keys"))
    implementation(project(":core:messages"))
    implementation(project(":core:sessions"))
    implementation(project(":security:policies"))
    implementation(project(":security:storage"))
    implementation(project(":security:clipboard"))
    implementation(project(":data:database"))
    implementation(project(":data:preferences"))
    implementation(project(":data:repository"))
    // :keyboard deliberately NOT included (spec 003, FR-006): the shipped
    // app no longer registers an IME service, removing the Compose-in-IME
    // crash class entirely. The module stays in settings.gradle.kts and
    // keeps compiling in CI; it may return behind a flag in a future spec.
    implementation(project(":plugins:api"))
    implementation(project(":plugins:registry"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // v0.3.0: system fingerprint prompt + its FragmentActivity host.
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.hilt.navigation.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
