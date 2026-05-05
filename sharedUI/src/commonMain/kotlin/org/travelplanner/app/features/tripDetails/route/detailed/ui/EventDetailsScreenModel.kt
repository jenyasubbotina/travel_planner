package org.travelplanner.app.features.tripDetails.route.detailed.ui

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.ReverseGeocoder
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.formatHoursDuration
import org.travelplanner.app.core.stripDurationSuffix
import org.travelplanner.app.data.EventRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.data.mimeTypeForFileName
import org.travelplanner.app.core.VersionConflictException
import org.travelplanner.app.features.tripDetails.route.detailed.data.toDto
import org.travelplanner.app.features.tripDetails.route.ui.EventEditData
import kotlin.time.Clock

private data class LocalUiState(
    val isEditing: Boolean = false,
    val editData: EventEditData = EventEditData(),
    val isSyncing: Boolean = false,
)

class EventDetailsScreenModel(
    private val tripId: String,
    private val eventId: String,
    private val eventRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val userSession: UserSession,
    private val tripRepository: TripRepository,
    private val reverseGeocoder: ReverseGeocoder,
) : ReactiveScreenModel<EventDetailsState, EventIntent, EventEffect>() {
    private val _localUiState = MutableStateFlow(LocalUiState())

    init {
        screenModelScope.launch {
            eventRepository.refreshPointFiles(tripId, eventId)
        }
    }

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
                    mimeType = "image/jpeg",
                    category = "PHOTO",
                )
            }

            is EventIntent.AddDocument -> {
                uploadFile(
                    intent.bytes,
                    intent.fileName,
                    mimeType = mimeTypeForFileName(intent.fileName),
                    category = "DOCUMENT",
                )
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
                screenModelScope.launch {
                    val resolved = reverseGeocoder.reverseGeocode(intent.lat, intent.lng) ?: return@launch
                    _localUiState.update { current ->
                        if (current.editData.latitude != intent.lat ||
                            current.editData.longitude != intent.lng
                        ) {
                            current
                        } else {
                            current.copy(editData = current.editData.copy(address = resolved))
                        }
                    }
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
                        address = current.address ?: "",
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
                        duration = stripDurationSuffix(current.duration),
                        status = current.status,
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
        mimeType: String,
        category: String,
    ) {
        screenModelScope.launch {
            try {
                eventRepository.uploadPointFile(tripId, eventId, bytes, fileName, mimeType)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
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
                        address = edit.address.ifBlank { null },
                        description = edit.description,
                        time = edit.time,
                        cost = edit.cost.toDoubleOrNull() ?: 0.0,
                        duration = formatHoursDuration(edit.duration).ifBlank { currentDto.duration },
                        status = edit.status,
                        latitude = edit.latitude ?: currentDto.latitude,
                        longitude = edit.longitude ?: currentDto.longitude,
                        participantIds = edit.participantIds,
                    )
                eventRepository.updateEvent(
                    tripId,
                    eventId,
                    updatedDto,
                )
                _localUiState.update { it.copy(isEditing = false) }
            } catch (e: VersionConflictException) {
                sendEffect(EventEffect.ShowError("Конфликт данных!"))
            } finally {
                _localUiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun addComment(text: String) {
        if (text.isBlank()) return
        screenModelScope.launch {
            try {
                eventRepository.addPointComment(tripId, eventId, text)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    private fun addLink(
        title: String,
        url: String,
    ) {
        if (title.isBlank() || url.isBlank()) return
        screenModelScope.launch {
            try {
                eventRepository.addPointLink(tripId, eventId, title, url)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    private fun deleteEvent() {
        screenModelScope.launch {
            eventRepository.deleteEvent(tripId, eventId)
            sendEffect(EventEffect.PopScreen)
        }
    }
}
