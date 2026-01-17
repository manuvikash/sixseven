import java.util.Properties

// Load local.properties
val localProperties = Properties()
val localPropertiesFile = file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        
        // Meta WDA SDK from GitHub Packages
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = localProperties.getProperty("gpr.user") 
                    ?: System.getenv("GITHUB_USER") 
                    ?: "token"
                password = localProperties.getProperty("github_token") 
                    ?: System.getenv("GITHUB_TOKEN") 
                    ?: ""
            }
        }
    }
}

rootProject.name = "67"
include(":app")
