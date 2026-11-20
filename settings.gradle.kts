pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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
