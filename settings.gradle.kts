pluginManagement {
    repositories {
        maven { url = rootDir.resolve("google-mirror").toURI() }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = rootDir.resolve("google-mirror").toURI() }
        maven { url = uri("C:/Users/NMSoldatov/AppData/Local/Temp/opencode/m2local") }
        google()
        mavenCentral()
    }
}

rootProject.name = "CB"
include(":app")
