pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "conveyance-h2g2"

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Conveyance release tags are published publicly by JitPack. No credentials are needed,
        // including when JitPack itself builds h2g2.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroupAndSubgroups("com.github.HereLiesAz.conveyance") }
        }
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
