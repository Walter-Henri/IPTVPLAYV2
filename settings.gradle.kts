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
        maven("https://chaquo.com/maven")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://chaquo.com/maven")
        maven("https://jitpack.io")
        maven("https://artifacts.videolan.org/android/")
    }
}

rootProject.name = "M3U"

// Módulo principal (fusão de universal + plugin)
include(":app:universal")
include(":app:m3u-plugin")

// Core modules
include(":core", ":core:foundation", ":core:plugin", ":core:media-resolver", ":core:aidl")

// Data module
include(":data")

// Business modules
include(
    ":business:foryou",
    ":business:favorite",
    ":business:setting",
    ":business:playlist",
    ":business:playlist-configuration",
    ":business:channel",
    ":business:plugin",
)

// Internationalization
include(":i18n")

// Lint modules
include(
    ":lint:annotation",
    ":lint:processor"
)