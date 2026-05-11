package org.travelplanner.app.core

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

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
                MapsLaunchMode.DIRECTIONS ->
                    "http://maps.apple.com/?daddr=$latitude,$longitude&dirflg=d"
                MapsLaunchMode.VIEW ->
                    "http://maps.apple.com/?ll=$latitude,$longitude&q=" +
                        (label?.takeIf { it.isNotBlank() } ?: "$latitude,$longitude")
            }
        val url = NSURL.URLWithString(urlString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}
