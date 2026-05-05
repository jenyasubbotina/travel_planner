package org.travelplanner.app.data

import org.travelplanner.app.OutboxEntry
import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.Trip

data class TripDetailsSyncState(
    val syncState: SyncState = SyncState.UP_TO_DATE,
    val trip: Trip? = null,
    val pendingCount: Long = 0L,
    val conflicts: List<OutboxEntry> = emptyList(),
    val deadEntries: List<OutboxEntry> = emptyList(),
    val depthAlert: Boolean = false,
) : UiState

sealed interface TripDetailsIntent : UiIntent {
    data object PerformSync : TripDetailsIntent

    data class DiscardDeadEntry(
        val entryId: String,
    ) : TripDetailsIntent

    data class RetryEntry(
        val entryId: String,
    ) : TripDetailsIntent
}

sealed interface TripDetailsEffect : UiEffect {
    data object KickUser : TripDetailsEffect
}
