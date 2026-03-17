package org.travelplanner.app.features.tripList

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.Trip

enum class TripFilter { ALL, UPCOMING, ARCHIVED }

data class TripListState(
    val trips: List<Trip> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: TripFilter = TripFilter.ALL,
) : UiState

sealed interface TripListIntent : UiIntent {
    data class Search(
        val query: String,
    ) : TripListIntent

    data class FilterChange(
        val filter: TripFilter,
    ) : TripListIntent

    data class RequestJoin(
        val code: String,
    ) : TripListIntent

    data object Refresh : TripListIntent

    data object DismissMessage : TripListIntent
}

sealed interface TripListEffect : UiEffect {
    data class ShowMessage(
        val message: String,
    ) : TripListEffect
}
