// Builds and tests only the pure-Kotlin (JVM) modules, without the Android Gradle Plugin or
// SDK. Used where Google Maven is unreachable and as a fast pre-check:
//   gradle -p tools/jvm-check test
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "jvm-check"

val jvmModules = listOf(
    ":core:model",
    ":dialer:domain",
    ":contacts:domain",
    ":calling:domain",
    ":recording:domain",
    ":recordings:domain",
)
jvmModules.forEach { path ->
    include(path)
    project(path).projectDir = file("../.." + path.replace(':', '/'))
}
