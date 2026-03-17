package org.travelplanner.app.data

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState

data class TripDetailsSyncState(
    val syncState: SyncState = SyncState.UP_TO_DATE,
) : UiState

sealed interface TripDetailsIntent : UiIntent {
    data object PerformSync : TripDetailsIntent
}

sealed interface TripDetailsEffect : UiEffect {
    data object KickUser : TripDetailsEffect
}
