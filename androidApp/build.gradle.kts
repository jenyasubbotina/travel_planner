import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.travelplanner.app.androidApp"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36

        applicationId = "org.travelplanner.app.androidApp"
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(libs.androidx.activityCompose)

    implementation(libs.koin.core)
    implementation(libs.koin.compose)

    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    implementation("cafe.adriel.voyager:voyager-screenmodel:1.1.0-beta03")
    implementation("cafe.adriel.voyager:voyager-koin:1.1.0-beta03")
    implementation("cafe.adriel.voyager:voyager-tab-navigator:1.1.0-beta03")

    implementation(libs.kstore)
    implementation(libs.kstore.file)
    implementation("com.squareup.okio:okio:3.16.2")
    implementation(libs.androidx.lifecycle.process)
}
