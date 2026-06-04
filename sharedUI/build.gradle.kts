import org.gradle.kotlin.dsl.implementation
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.buildConfig)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    androidTarget() // We need the deprecated target to have working previews

    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.resources)
            api(libs.compose.ui.tooling.preview)
            api(libs.compose.material3)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            // implementation(libs.ktor.client.logging)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.voyager.navigator)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kstore)

            implementation(libs.coil)
            implementation(libs.coil.network.ktor)

            implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

            implementation("cafe.adriel.voyager:voyager-screenmodel:1.1.0-beta03")
            implementation("cafe.adriel.voyager:voyager-koin:1.1.0-beta03")
            implementation("cafe.adriel.voyager:voyager-tab-navigator:1.1.0-beta03")

            implementation("app.cash.sqldelight:coroutines-extensions:2.2.1")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.logging)
            implementation(libs.sqlDelight.driver.android)
            implementation(libs.kstore.file)

            implementation(libs.androidx.activityCompose)
            implementation(libs.androidx.work.runtime.ktx)

            implementation(libs.yandex.mapkit)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.logging)
            implementation(libs.sqlDelight.driver.sqlite)
            implementation(libs.kstore.file)
            implementation(libs.coil)
            implementation(libs.coil.network.ktor)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.ktor.client.logging)
            implementation(libs.sqlDelight.driver.native)
            implementation(libs.kstore.file)
            implementation(libs.coil)
            implementation(libs.coil.network.ktor)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.logging)
            implementation(libs.sqlDelight.driver.js)
            implementation("app.cash.sqldelight:async-extensions:2.2.1")
            implementation(libs.kstore.storage)
            implementation(npm("sql.js", "1.13.0"))
            implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.2.1"))
            implementation(devNpm("copy-webpack-plugin", "13.0.0"))
            val coilVersion = "3.3.0"
            implementation("io.coil-kt.coil3:coil-compose-wasm-js:$coilVersion")
            implementation("io.coil-kt.coil3:coil-network-ktor3-wasm-js:$coilVersion")
        }
    }

    targets
        .withType<KotlinNativeTarget>()
        .matching { it.konanTarget.family.isAppleFamily }
        .configureEach {
            binaries {
                framework {
                    baseName = "SharedUI"
                    isStatic = true
                }
            }
        }
}

dependencies {
    debugImplementation(libs.compose.ui.tooling)
}
android {
    namespace = "org.travelplanner.app"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

buildConfig {
    // BuildConfig configuration here.
    // https://github.com/gmazzo/gradle-buildconfig-plugin#usage-in-kts
}

sqldelight {
    databases {
        create("MyDatabase") {
            // Database configuration here.
            // https://cashapp.github.io/sqldelight
            packageName.set("org.travelplanner.app.db")
        }
    }
}
