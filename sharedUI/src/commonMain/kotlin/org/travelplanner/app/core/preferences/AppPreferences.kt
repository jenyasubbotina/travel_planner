package org.travelplanner.app.core.preferences

import kotlinx.serialization.Serializable

@Serializable
data class AppPreferences(
    val notificationsEnabled: Boolean = true,
    val lightTheme: Boolean = true,
)
