import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":sharedUI"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.kstore)
                implementation(libs.kstore.storage)
                implementation(libs.voyager.navigator)
                implementation("cafe.adriel.voyager:voyager-screenmodel:1.1.0-beta03")
                implementation("cafe.adriel.voyager:voyager-koin:1.1.0-beta03")
                implementation("cafe.adriel.voyager:voyager-tab-navigator:1.1.0-beta03")
                implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            }
        }
    }
}
