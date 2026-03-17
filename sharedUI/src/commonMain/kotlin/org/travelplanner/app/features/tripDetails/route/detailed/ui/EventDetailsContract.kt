package org.travelplanner.app.features.tripDetails.route.detailed.ui

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.Event
import org.travelplanner.app.domain.EventComment
import org.travelplanner.app.domain.EventFile
import org.travelplanner.app.domain.EventLink
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.features.tripDetails.route.ui.EventEditData

data class EventDetailsState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val comments: List<EventComment> = emptyList(),
    val links: List<EventLink> = emptyList(),
    val files: List<EventFile> = emptyList(),
    val participants: List<Participant> = emptyList(),
    val isSyncing: Boolean = false,
    val isEditing: Boolean = false,
    val editData: EventEditData = EventEditData(),
    val currency: String = "¥",
) : UiState

sealed interface EventIntent : UiIntent {
    data object OpenEditor : EventIntent

    data object CloseEditor : EventIntent

    data object SaveEditorChanges : EventIntent

    data class UpdateEditorField(
        val mutation: EventEditData.() -> EventEditData,
    ) : EventIntent

    data class AddPhoto(
        val bytes: ByteArray,
    ) : EventIntent

    data class AddDocument(
        val bytes: ByteArray,
        val fileName: String,
    ) : EventIntent

    data object OpenMapPicker : EventIntent

    data object CloseMapPicker : EventIntent

    data class SetLocation(
        val lat: Double,
        val lng: Double,
    ) : EventIntent

    data class AddComment(
        val text: String,
    ) : EventIntent

    data class AddLink(
        val title: String,
        val url: String,
    ) : EventIntent

    data object DeleteEvent : EventIntent
}

sealed interface EventEffect : UiEffect {
    data object PopScreen : EventEffect

    data class ShowError(
        val message: String,
    ) : EventEffect
}
