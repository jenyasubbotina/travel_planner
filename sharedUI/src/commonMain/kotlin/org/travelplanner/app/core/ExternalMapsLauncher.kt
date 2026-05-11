package org.travelplanner.app.core

import androidx.compose.runtime.Composable

enum class MapsLaunchMode { VIEW, DIRECTIONS }

@Composable
expect fun rememberExternalMapsLauncher(): ExternalMapsLauncher

expect class ExternalMapsLauncher {
    fun launch(
        latitude: Double,
        longitude: Double,
        label: String?,
        mode: MapsLaunchMode,
    )
}
