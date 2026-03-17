package org.travelplanner.app.features.tripDetails.route.ui

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.data.EventRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.toDto
import org.travelplanner.app.features.tripDetails.route.detailed.ui.EventIntent

private data class LocalUiState(
    val viewMode: ItineraryViewMode = ItineraryViewMode.LIST,
    val selectedDayIndex: Int = 0,
    val selectedEventId: Long? = null,
    val isEditorVisible: Boolean = false,
    val editorData: EventEditData = EventEditData(),
)

class ItineraryScreenModel(
    private val tripId: Long,
    private val tripRepository: TripRepository,
    private val eventsRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
) : ReactiveScreenModel<ItineraryState, ItineraryIntent, ItineraryEffect>() {
    private val _localUiState = MutableStateFlow(LocalUiState())

    init {
        screenModelScope.launch {
            eventsRepository.syncEvents(tripId)
        }
    }

    override val state: StateFlow<ItineraryState> =
        combine(
            _localUiState,
            eventsRepository.getEventsFlow(tripId),
            tripRepository.getTripById(tripId),
            participantRepository.getParticipantsFlow(tripId),
        ) { local, allEvents, trip, participants ->
            val dayEvents =
                allEvents
                    .filter { it.dayIndex == local.selectedDayIndex }
                    .sortedBy { it.time }

            ItineraryState(
                viewMode = local.viewMode,
                selectedDayIndex = local.selectedDayIndex,
                events = dayEvents,
                tripStartDate = trip?.startDate ?: 0L,
                selectedEventId = local.selectedEventId,
                isEditorVisible = local.isEditorVisible,
                editorData = local.editorData,
                participants = participants,
                currency = trip?.currency ?: "¥",
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ItineraryState())

    override fun handleIntent(intent: ItineraryIntent) {
        when (intent) {
            is ItineraryIntent.SetViewMode -> {
                _localUiState.update { it.copy(viewMode = intent.mode) }
            }

            is ItineraryIntent.SelectDay -> {
                _localUiState.update { it.copy(selectedDayIndex = intent.index) }
            }

            is ItineraryIntent.NavigateToEventOnMap -> {
                _localUiState.update {
                    it.copy(
                        selectedEventId = intent.eventId,
                        viewMode = ItineraryViewMode.MAP,
                    )
                }
            }

            is ItineraryIntent.CreateNewEvent -> {
                _localUiState.update {
                    it.copy(
                        isEditorVisible = true,
                        editorData =
                            EventEditData(
                                dayIndex = it.selectedDayIndex,
                                time = "09:00",
                                eventId = null,
                            ),
                    )
                }
            }

            is ItineraryIntent.EditEvent -> {
                val event = intent.event
                _localUiState.update {
                    it.copy(
                        isEditorVisible = true,
                        editorData =
                            EventEditData(
                                eventId = event.id,
                                dayIndex = event.dayIndex,
                                title = event.title,
                                subtitle = event.subtitle,
                                time = event.time,
                                description = event.description ?: "",
                                cost =
                                    if (event.cost > 0) {
                                        event.cost
                                            .toInt()
                                            .toString()
                                    } else {
                                        ""
                                    },
                                latitude = event.latitude,
                                longitude = event.longitude,
                            ),
                    )
                }
            }

            is ItineraryIntent.EditorAction -> {
                handleEditorAction(intent.action)
            }
        }
    }

    private fun handleEditorAction(action: EventIntent) {
        when (action) {
            is EventIntent.CloseEditor -> {
                _localUiState.update { it.copy(isEditorVisible = false) }
            }

            is EventIntent.UpdateEditorField -> {
                _localUiState.update { it.copy(editorData = action.mutation(it.editorData)) }
            }

            is EventIntent.OpenMapPicker -> {
                _localUiState.update { it.copy(editorData = it.editorData.copy(isMapPickerOpen = true)) }
            }

            is EventIntent.CloseMapPicker -> {
                _localUiState.update { it.copy(editorData = it.editorData.copy(isMapPickerOpen = false)) }
            }

            is EventIntent.SetLocation -> {
                _localUiState.update {
                    it.copy(
                        editorData =
                            it.editorData.copy(
                                latitude = action.lat,
                                longitude = action.lng,
                                isMapPickerOpen = false,
                            ),
                    )
                }
            }

            is EventIntent.SaveEditorChanges -> {
                saveEvent()
            }

            else -> {}
        }
    }

    private fun saveEvent() {
        val data = state.value.editorData
        val originalEvent = state.value.events.find { it.id == data.eventId }

        screenModelScope.launch {
            if (data.eventId == null) {
                val dto =
                    EventDto(
                        id = "",
                        tripId = tripId,
                        dayIndex = data.dayIndex,
                        time = data.time,
                        title = data.title,
                        subtitle = data.subtitle,
                        description = data.description,
                        duration = "1 ч",
                        cost = data.cost.toDoubleOrNull() ?: 0.0,
                        actualCost = 0.0,
                        status = "NONE",
                        category = "SIGHT",
                        latitude = data.latitude ?: 0.0,
                        longitude = data.longitude ?: 0.0,
                        address = data.subtitle,
                        links = emptyList(),
                        comments = emptyList(),
                        files = emptyList(),
                        participantIds = data.participantIds,
                    )
                eventsRepository.addEventOnline(tripId, dto)
            } else {
                if (originalEvent != null) {
                    val currentDto = originalEvent.toDto()
                    val updatedDto =
                        currentDto.copy(
                            dayIndex = data.dayIndex,
                            time = data.time,
                            title = data.title,
                            subtitle = data.subtitle,
                            description = data.description,
                            cost = data.cost.toDoubleOrNull() ?: 0.0,
                            latitude = data.latitude ?: currentDto.latitude,
                            longitude = data.longitude ?: currentDto.longitude,
                            participantIds = data.participantIds,
                        )
                    eventsRepository.updateEventOnline(
                        tripId = tripId,
                        eventId = data.eventId,
                        remoteId = originalEvent.remoteId ?: "",
                        dto = updatedDto,
                    )
                }
            }

            handleIntent(ItineraryIntent.EditorAction(EventIntent.CloseEditor))
        }
    }
}
