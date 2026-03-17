import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":sharedUI"))

    implementation(libs.koin.compose)
    implementation(libs.koin.core)
    implementation(libs.voyager.navigator)

    implementation("cafe.adriel.voyager:voyager-screenmodel:1.1.0-beta03")
    implementation("cafe.adriel.voyager:voyager-koin:1.1.0-beta03")
    implementation("cafe.adriel.voyager:voyager-transitions:1.1.0-beta03")

    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    implementation(libs.kstore)
    implementation(libs.kstore.file)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TravelPlanner"
            packageVersion = "1.0.0"

            linux {
                iconFile.set(project.file("appIcons/LinuxIcon.png"))
            }
            windows {
                iconFile.set(project.file("appIcons/WindowsIcon.ico"))
            }
            macOS {
                iconFile.set(project.file("appIcons/MacosIcon.icns"))
                bundleID = "org.travelplanner.app.desktopApp"
            }
        }
    }
}
