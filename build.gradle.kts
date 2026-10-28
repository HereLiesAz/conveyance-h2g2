import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    `maven-publish`
}

group = "com.hereliesaz.conveyance"
// Read from version.properties, the same file CI stamps releases from. JitPack overrides both
// group and version from the git ref it builds; this is what a local `publishToMavenLocal` produces.
version = Properties().run {
    file("version.properties").inputStream().use { load(it) }
    listOf("versionMajor", "versionMinor", "versionPatch", "versionBuild")
        .joinToString(".") { getProperty(it).trim() }
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    androidLibrary {
        namespace = "com.hereliesaz.conveyance.h2g2"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm("desktop")
    js {
        browser()
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // Pinned to Conveyance's 0.5.0 release tag (JitPack build status "ok", all core/compose
            // target publications present). A floating main-SNAPSHOT makes KMP metadata
            // non-reproducible and can resolve stale per-target publications through JitPack.
            val conveyanceRevision = "0.5.0"
            api("com.github.HereLiesAz.Conveyance:conveyance-core:$conveyanceRevision")
            api("com.github.HereLiesAz.Conveyance:conveyance-compose:$conveyanceRevision")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // Templates.kt is composables, and the defect an audit found there (every Offer
            // missing `.tell(owesTell, weight).clickable { engage() }`, so every element rendered
            // but was inert) is only observable by actually composing one and clicking it. This is
            // the same harness conveyance-compose's own commonTest uses for its Offer tests.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
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
                "The h2g2 style system -- hues, ground-rotation surfaces, workflow routes, and the 8-step Jost type scale -- ported from HG2Gui.",
            )
            url.set("https://github.com/HereLiesAz/conveyance-h2g2")
            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
        }
    }
}
