import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val yandexMapKitApiKey: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    props.getProperty("yandex.mapkit.apiKey")
        ?: System.getenv("YANDEX_MAPKIT_API_KEY")
        ?: ""
}

android {
    namespace = "org.travelplanner.app.androidApp"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36

        applicationId = "org.travelplanner.app.androidApp"
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "YANDEX_MAPKIT_API_KEY", "\"$yandexMapKitApiKey\"")
    }

    buildFeatures {
        buildConfig = true
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

    implementation(libs.ktor.client.core)
    implementation(libs.wormaceptor.api)
    debugImplementation(libs.wormaceptor.impl.persistence)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.yandex.mapkit)
}
