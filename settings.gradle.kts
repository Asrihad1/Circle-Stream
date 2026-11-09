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
        maven { url = java.net.URI("https://jitpack.io") }
        maven { url = java.net.URI("https://maven.wortise.com/artifactory/public") }
        maven { url = java.net.URI("https://cboost.jfrog.io/artifactory/chartboost-ads/") }
        maven { url = java.net.URI("https://artifact.bytedance.com/repository/pangle") }
        maven { url = java.net.URI("https://artifactory.bidmachine.io/bidmachine") }
    }
}

rootProject.name = "CircleStream"
include(":app")

