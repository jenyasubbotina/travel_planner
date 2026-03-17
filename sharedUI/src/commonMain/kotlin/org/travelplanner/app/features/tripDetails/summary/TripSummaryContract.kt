package org.travelplanner.app.features.tripDetails.summary

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.Event
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.domain.Trip

data class TripSummaryState(
    val trip: Trip? = null,
    val participants: List<Participant> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val events: List<Event> = emptyList(),
) : UiState

sealed interface TripSummaryIntent : UiIntent {
    data class UpdateBudget(
        val newBudget: Double,
    ) : TripSummaryIntent
}

sealed interface TripSummaryEffect : UiEffect
