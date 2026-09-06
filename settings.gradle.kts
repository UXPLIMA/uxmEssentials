pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// Local development: when uxmLib is checked out as a sibling directory, build against it directly as a
// composite build so library changes are picked up without a publish step. Without the sibling checkout the
// published com.uxplima.uxmlib artifacts resolve from mavenLocal normally.
//
// Both directory names are accepted. The GitHub repository was renamed from uxmLib to uxm-lib, so a fresh
// clone lands in ../uxm-lib while older checkouts (and CI, which clones into a fixed path) are still ../uxmLib.
// Matching only one of them would drop the composite silently and fall back to whatever mavenLocal happens to
// hold, which reads as a stale library rather than a missing one.
//
// The workspace puts every plugin under plugins/ and keeps the library at the root, because the library is
// what the plugins build on rather than one of them. That checkout is two levels up, so it is named here too.
val uxmLibDir = listOf("../uxmLib", "../uxm-lib", "../../uxm-lib")
        .map(::file)
        .firstOrNull { it.isDirectory }

if (uxmLibDir != null) {
    includeBuild(uxmLibDir)
}

rootProject.name = "uxmEssentials"

include(
    ":api",
    ":bukkit-api",
    ":core",
    ":bukkit-adapter",
    ":persistence-adapter",
    ":migration",
    ":velocity-adapter",
    ":discord-adapter",
    ":redis-adapter",
    ":rest-adapter",
)
