package org.travelplanner.app.core

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun rememberExternalMapsLauncher(): ExternalMapsLauncher = ExternalMapsLauncher()

actual class ExternalMapsLauncher {
    actual fun launch(
        latitude: Double,
        longitude: Double,
        label: String?,
        mode: MapsLaunchMode,
    ) {
        val urlString =
            when (mode) {
                MapsLaunchMode.DIRECTIONS -> {
                    "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude"
                }

                MapsLaunchMode.VIEW -> {
                    "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
                }
            }
        window.open(urlString, "_blank")
    }
}
