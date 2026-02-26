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
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        ivy {
            url = uri("https://github.com/rkz-app/airgap/releases/download")
            patternLayout {
                artifact("[revision]/[artifact].[ext]")
            }
            metadataSources {
                artifact()
            }
        }
    }
}

rootProject.name = "AirgapSDK"
include(":lib")
include(":lib:generator")
include(":lib:consumer")
include(":example")
