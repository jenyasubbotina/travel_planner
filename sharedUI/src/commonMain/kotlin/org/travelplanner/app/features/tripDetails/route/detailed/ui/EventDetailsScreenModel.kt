package org.travelplanner.app.features.tripDetails.route.detailed.ui

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.EventRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.features.tripDetails.history.ConflictException
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventCommentDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventLinkDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.toDto
import org.travelplanner.app.features.tripDetails.route.ui.EventEditData
import kotlin.time.Clock

private data class LocalUiState(
    val isEditing: Boolean = false,
    val editData: EventEditData = EventEditData(),
    val isSyncing: Boolean = false,
)

class EventDetailsScreenModel(
    private val tripId: Long,
    private val eventId: Long,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val userSession: UserSession,
    private val tripRepository: TripRepository,
) : ReactiveScreenModel<EventDetailsState, EventIntent, EventEffect>() {
    fun resolveUrl(path: String?): String? = eventRepository.resolveUrl(path)

    private val _localUiState = MutableStateFlow(LocalUiState())

    override val state: StateFlow<EventDetailsState> =
        combine(
            _localUiState,
            eventRepository.getEventFlow(eventId),
            participantRepository.getParticipantsFlow(tripId),
            tripRepository.getTripById(tripId),
        ) { local, entity, participants, trip ->
            EventDetailsState(
                isLoading = false,
                event = entity,
                comments = entity?.comments ?: emptyList(),
                links = entity?.links ?: emptyList(),
                files = entity?.files ?: emptyList(),
                participants = participants,
                isEditing = local.isEditing,
                editData = local.editData,
                isSyncing = local.isSyncing,
                currency = trip?.currency ?: "¥",
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), EventDetailsState())

    override fun handleIntent(intent: EventIntent) {
        when (intent) {
            is EventIntent.OpenEditor -> {
                openEditor()
            }

            is EventIntent.CloseEditor -> {
                _localUiState.update { it.copy(isEditing = false, editData = EventEditData()) }
            }

            is EventIntent.SaveEditorChanges -> {
                saveChanges()
            }

            is EventIntent.UpdateEditorField -> {
                _localUiState.update { it.copy(editData = intent.mutation(it.editData)) }
            }

            is EventIntent.AddPhoto -> {
                uploadFile(
                    intent.bytes,
                    "photo_${Clock.System.now().toEpochMilliseconds()}.jpg",
                    "PHOTO",
                )
            }

            is EventIntent.AddDocument -> {
                uploadFile(intent.bytes, intent.fileName, "DOCUMENT")
            }

            is EventIntent.OpenMapPicker -> {
                _localUiState.update { it.copy(editData = it.editData.copy(isMapPickerOpen = true)) }
            }

            is EventIntent.CloseMapPicker -> {
                _localUiState.update { it.copy(editData = it.editData.copy(isMapPickerOpen = false)) }
            }

            is EventIntent.SetLocation -> {
                _localUiState.update {
                    it.copy(
                        editData =
                            it.editData.copy(
                                latitude = intent.lat,
                                longitude = intent.lng,
                                isMapPickerOpen = false,
                            ),
                    )
                }
            }

            is EventIntent.AddComment -> {
                addComment(intent.text)
            }

            is EventIntent.AddLink -> {
                addLink(intent.title, intent.url)
            }

            is EventIntent.DeleteEvent -> {
                deleteEvent()
            }
        }
    }

    private fun openEditor() {
        val current = state.value.event ?: return
        _localUiState.update {
            it.copy(
                isEditing = true,
                editData =
                    EventEditData(
                        title = current.title,
                        subtitle = current.subtitle,
                        description = current.description ?: "",
                        time = current.time,
                        cost =
                            if (current.cost > 0) {
                                current.cost
                                    .toInt()
                                    .toString()
                            } else {
                                ""
                            },
                        latitude = current.latitude,
                        longitude = current.longitude,
                        participantIds = current.participantIds,
                    ),
            )
        }
    }

    private fun uploadFile(
        bytes: ByteArray,
        fileName: String,
        type: String,
    ) {
        val entity = state.value.event ?: return
        screenModelScope.launch {
            eventRepository.uploadFileAndAdd(
                tripId,
                eventId,
                entity.toDto(),
                bytes,
                fileName,
                type,
            )
        }
    }

    private fun saveChanges() {
        val entity = state.value.event ?: return
        val edit = state.value.editData
        screenModelScope.launch {
            _localUiState.update { it.copy(isSyncing = true) }
            try {
                val currentDto = entity.toDto()
                val updatedDto =
                    currentDto.copy(
                        title = edit.title,
                        subtitle = edit.subtitle,
                        description = edit.description,
                        time = edit.time,
                        cost = edit.cost.toDoubleOrNull() ?: 0.0,
                        latitude = edit.latitude ?: currentDto.latitude,
                        longitude = edit.longitude ?: currentDto.longitude,
                        participantIds = edit.participantIds,
                    )
                eventRepository.updateEventOnline(
                    tripId,
                    eventId,
                    entity.remoteId ?: "",
                    updatedDto,
                )
                _localUiState.update { it.copy(isEditing = false) }
            } catch (e: ConflictException) {
                sendEffect(EventEffect.ShowError("Конфликт данных!"))
            } finally {
                _localUiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun addComment(text: String) {
        val entity = state.value.event ?: return
        val currentUser = userSession.currentUser.value ?: return
        if (text.isBlank()) return
        screenModelScope.launch {
            val newComment =
                EventCommentDto(
                    userId = currentUser.id.toString(),
                    userName = currentUser.name,
                    text = text,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
            val dto = entity.toDto()
            eventRepository.updateEventOnline(
                tripId,
                eventId,
                entity.remoteId ?: "",
                dto.copy(comments = dto.comments + newComment),
            )
        }
    }

    private fun addLink(
        title: String,
        url: String,
    ) {
        val entity = state.value.event ?: return
        if (title.isBlank() || url.isBlank()) return
        screenModelScope.launch {
            val dto = entity.toDto()
            eventRepository.updateEventOnline(
                tripId,
                eventId,
                entity.remoteId ?: "",
                dto.copy(links = dto.links + EventLinkDto(title, url)),
            )
        }
    }

    private fun deleteEvent() {
        val entity = state.value.event ?: return
        screenModelScope.launch {
            eventRepository.deleteEventOnline(tripId, eventId, entity.remoteId)
            sendEffect(EventEffect.PopScreen)
        }
    }
}
