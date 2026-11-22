pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // v1.6.0-beta.7 build note (2026-11-22): Maven Central AND Aliyun
        // both fail from this sandbox right now (429 / 502) — the Tencent
        // mirror answers 200, so it goes FIRST. Same mirror-fallback
        // pattern the v1.5.9/v1.8.0 builds used, rotated to whichever
        // mirror is actually up.
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public")
        mavenCentral()
        gradlePluginPortal()
        // Sandbox builds: mirror fallbacks for when the shared egress IP
        // gets rate-limited (429) or a mirror gap occurs upstream.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // v1.5.9 build note: Maven Central 429s Gradle's user-agent from
        // this sandbox while curl succeeds — Aliyun mirrors both repos
        // (verified 200) so it goes FIRST. Same fix the v1.8.0 build used.
        // v1.6.0-beta.7: Aliyun 502s today; Tencent leads, Aliyun/google
        // follow as fallbacks.
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}

rootProject.name = "turnypt"

include(":app")
include(":keyboard")

include(":core:common")
include(":core:crypto")
include(":core:stego")
include(":core:messages")
include(":core:keys")
include(":core:sessions")

include(":data:database")
include(":data:repository")
include(":data:preferences")

include(":security:storage")
include(":security:clipboard")
include(":security:policies")

include(":plugins:api")
include(":plugins:registry")
