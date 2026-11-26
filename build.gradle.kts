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
val localVersion = Properties().run {
    file("version.properties").inputStream().use { load(it) }
    listOf("versionMajor", "versionMinor", "versionPatch", "versionBuild")
        .joinToString(".") { getProperty(it).trim() }
}
version = providers.gradleProperty("publicationVersion").orElse(localVersion).get()

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())

    androidLibrary {
        namespace = "com.hereliesaz.conveyance.h2g2"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        // Compose resources (the Azrienoch cuts, strings) are packaged as Android assets.
        androidResources { enable = true }
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
            // The Conveyance release tag is in gradle/libs.versions.toml (`conveyance`), where Dependabot bumps it.
            api(libs.conveyance.core)
            api(libs.conveyance.compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.2")
            // Azrienoch (the bundled typeface) and the ported screens' strings are Compose resources.
            implementation(compose.components.resources)
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
        androidMain.dependencies {
            // WindowCompat, for H2g2Theme's status/navigation-bar icon tint (Theme.android.kt).
            implementation("androidx.core:core-ktx:1.19.1")
        }
        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// The generated `Res` class stays internal: a host reads the typeface through
// `azrienochFontFamily()` and friends (Type.kt), never through this library's resource ids.
compose.resources {
    packageOfResClass = "com.hereliesaz.conveyance.h2g2.resources"
    publicResClass = false
    generateResClass = always
}

// Kotlin Multiplatform registers one publication per target on its own; there is nothing to
// create here, only a shared description for whichever one a consumer ends up resolving.
publishing {
    if (providers.gradleProperty("publicationVersion").isPresent) {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/HereLiesAz/conveyance-h2g2")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Conveyance h2g2")
            description.set(
                "The h2g2 design system -- hues, grounds, the Azrienoch type scale, motion, and HG2Gui's screens and components as composables.",
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
