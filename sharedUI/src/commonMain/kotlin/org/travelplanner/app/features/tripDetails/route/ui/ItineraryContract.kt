package org.travelplanner.app.features.tripDetails.route.ui

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.Event
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.features.tripDetails.route.detailed.ui.EventIntent
import kotlin.time.Clock.System

enum class ItineraryViewMode { LIST, MAP }

data class ItineraryState(
    val viewMode: ItineraryViewMode = ItineraryViewMode.LIST,
    val selectedDayIndex: Int = 0,
    val events: List<Event> = emptyList(),
    val tripStartDate: Long = System.now().toEpochMilliseconds(),
    val dayCount: Int = 1,
    val eventsCountByDay: Map<Int, Int> = emptyMap(),
    val selectedEventId: String? = null,
    val isEditorVisible: Boolean = false,
    val editorData: EventEditData = EventEditData(),
    val participants: List<Participant> = emptyList(),
    val currency: String = "¥",
) : UiState

sealed interface ItineraryIntent : UiIntent {
    data class SetViewMode(
        val mode: ItineraryViewMode,
    ) : ItineraryIntent

    data class SelectDay(
        val index: Int,
    ) : ItineraryIntent

    data class NavigateToEventOnMap(
        val eventId: String,
    ) : ItineraryIntent

    data object CreateNewEvent : ItineraryIntent

    data class EditEvent(
        val event: Event,
    ) : ItineraryIntent

    data class EditorAction(
        val action: EventIntent,
    ) : ItineraryIntent
}

sealed interface ItineraryEffect : UiEffect
