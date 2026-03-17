package org.travelplanner.app.features.tripDetails.more.checklist.ui

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.ChecklistItem

data class ChecklistState(
    val items: List<ChecklistItem> = emptyList(),
    val totalParticipants: Int = 1,
    val currentUserId: String = "",
) : UiState

sealed interface ChecklistIntent : UiIntent {
    data class AddItem(
        val title: String,
        val isGroup: Boolean,
    ) : ChecklistIntent

    data class ToggleItem(
        val itemId: String,
    ) : ChecklistIntent

    data class DeleteItem(
        val itemId: String,
    ) : ChecklistIntent
}

sealed interface ChecklistEffect : UiEffect
