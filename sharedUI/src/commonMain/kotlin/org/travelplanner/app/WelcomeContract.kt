package org.travelplanner.app

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState

data class WelcomeState(
    val name: String = "",
) : UiState

sealed interface WelcomeIntent : UiIntent {
    data class NameChanged(
        val name: String,
    ) : WelcomeIntent

    data object Register : WelcomeIntent
}

sealed interface WelcomeEffect : UiEffect
