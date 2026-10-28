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
    }
}

rootProject.name = "MinimalDialer"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":dialer:domain", ":dialer")
include(":contacts:domain", ":contacts")
include(":calling:domain", ":calling:telecom", ":calling:ui")
include(":recording:domain", ":recording:engine")
include(":recordings:domain", ":recordings")
include(":settings")
