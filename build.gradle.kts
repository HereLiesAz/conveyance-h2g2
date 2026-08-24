plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    `maven-publish`
}

group = "com.hereliesaz.conveyance"
version = "0.1.0"

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    androidLibrary {
        namespace = "com.hereliesaz.conveyance.h2g2"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // Conveyance has no tagged release yet, so this resolves against `main` via JitPack.
            // Once Conveyance cuts a release tag, pin to that instead of `main-SNAPSHOT`.
            api("com.github.HereLiesAz.Conveyance:conveyance-core:main-SNAPSHOT")
            api("com.github.HereLiesAz.Conveyance:conveyance-compose:main-SNAPSHOT")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Kotlin Multiplatform registers one publication per target on its own; there is nothing to
// create here, only a shared description for whichever one a consumer ends up resolving.
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Conveyance h2g2")
            description.set(
                "The h2g2 style system -- hues, ground-rotation surfaces, and the 8-step Jost type scale -- ported from HG2Gui.",
            )
        }
    }
}
