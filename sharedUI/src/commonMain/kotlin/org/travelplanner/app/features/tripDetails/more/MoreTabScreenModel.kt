package org.travelplanner.app.features.tripDetails.more

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.TripEvent
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.EventRepository
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.NetworkState
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.MediaType
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.domain.PendingUser
import org.travelplanner.app.domain.TripMediaItem
import org.travelplanner.app.features.tripDetails.more.checklist.data.ChecklistRepository
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventFileDto

class MoreTabScreenModel(
    private val tripId: Long,
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val eventRepository: EventRepository,
    private val checklistRepository: ChecklistRepository,
    private val globalSyncManager: GlobalSyncManager,
    private val userSession: UserSession,
    private val json: Json,
) : ReactiveScreenModel<MoreState, MoreTabIntent, MoreTabEffect>() {
    private val _pendingRequests = MutableStateFlow<List<PendingUser>>(emptyList())
    private val _isAddDialogVisible = MutableStateFlow(false)

    val currentUser = userSession.currentUser.value

    private val mediaItemsFlow =
        combine(
            tripRepository.getTripById(tripId).filterNotNull(),
            expenseRepository.getExpensesFlow(tripId),
            eventRepository.getEventsFlow(tripId),
        ) { trip, expenses, events ->
            val files = mutableListOf<TripMediaItem>()

            trip.imageUrl?.let {
                files.add(
                    TripMediaItem(
                        it,
                        "Обложка",
                        trip.title,
                        MediaType.IMAGE,
                        trip.startDate,
                        "Поездка",
                    ),
                )
            }

            expenses.forEach { exp ->
                exp.imageUrl?.let {
                    files.add(
                        TripMediaItem(
                            it,
                            exp.title,
                            "Чек (${exp.category})",
                            MediaType.IMAGE,
                            exp.date,
                            "Расходы",
                        ),
                    )
                }
            }

            events.forEach { ev ->
                val eventDate = trip.startDate + (ev.dayIndex * 86400000L)

                ev.files.forEach { file ->
                    files.add(
                        TripMediaItem(
                            url = file.url,
                            title = file.name,
                            subtitle = ev.title,
                            type = if (file.type == "PHOTO") MediaType.IMAGE else MediaType.DOCUMENT,
                            date = eventDate,
                            category = "События",
                        ),
                    )
                }
            }

            val tripFiles =
                try {
                    json.decodeFromString<List<EventFileDto>>(trip.filesJson ?: "[]")
                } catch (e: Exception) {
                    emptyList()
                }
            tripFiles.forEach { file ->
                files.add(
                    TripMediaItem(
                        url = file.url,
                        title = file.name,
                        subtitle = "Загруженный файл",
                        type = if (file.type == "PHOTO") MediaType.IMAGE else MediaType.DOCUMENT,
                        date = trip.startDate,
                        category = "Файлы",
                    ),
                )
            }

            files.sortedByDescending { it.date }
        }

    private val managementFlow =
        combine(
            participantRepository.getParticipantsFlow(tripId),
            _pendingRequests,
            _isAddDialogVisible,
            userSession.currentUser,
        ) { participants, pending, showDialog, user ->
            ManagementState(participants, pending, showDialog, user)
        }

    override val state: StateFlow<MoreState> =
        combine(
            tripRepository.getTripById(tripId).filterNotNull(),
            mediaItemsFlow,
            managementFlow,
            checklistRepository.getChecklistFlow(tripId).map { list ->
                list.filter { it.isGroup || it.ownerUserId == currentUser?.id.toString() }
            },
        ) { trip, media, mgmt, checklist ->
            MoreState(
                trip = trip,
                list = mgmt.participants,
                pendingRequests = mgmt.pending,
                isAddDialogVisible = mgmt.showDialog,
                currentUser = mgmt.user,
                mediaItems = media,
                checklist = checklist,
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), MoreState())

    private data class ManagementState(
        val participants: List<Participant>,
        val pending: List<PendingUser>,
        val showDialog: Boolean,
        val user: org.travelplanner.app.core.AppUser?,
    )

    init {
        loadPendingRequests()
        screenModelScope.launch {
            globalSyncManager.wsEvents.collect { event ->
                if (event is TripEvent.JoinRequestReceived || event is TripEvent.JoinRequestResolved) {
                    loadPendingRequests()
                }
            }
        }
    }

    override fun handleIntent(intent: MoreTabIntent) {
        when (intent) {
            is MoreTabIntent.ResolveRequest -> {
                resolveRequest(intent.userId, intent.approve)
            }

            is MoreTabIntent.RegenerateCode -> {
                regenerateCode()
            }

            is MoreTabIntent.ArchiveTrip -> {
                archiveTrip(intent.isArchived)
            }

            is MoreTabIntent.DeleteOrLeaveTrip -> {
                deleteOrLeaveTrip()
            }

            is MoreTabIntent.UploadFile -> {
                uploadFile(intent.bytes, intent.fileName)
            }

            is MoreTabIntent.ToggleChecklistItem -> {
                toggleChecklistItem(intent.itemId)
            }

            is MoreTabIntent.ShowAddDialog -> {
                _isAddDialogVisible.value = true
            }

            is MoreTabIntent.HideAddDialog -> {
                _isAddDialogVisible.value = false
            }

            is MoreTabIntent.DismissMessage -> {}
        }
    }

    fun resolveUrl(path: String?): String? = tripRepository.resolveUrl(path)

    private fun loadPendingRequests() {
        screenModelScope.launch {
            try {
                val trip = tripRepository.getTripById(tripId).filterNotNull().first()
                val isOwner = trip.ownerUserId == currentUser?.id?.toString()
                if (isOwner) {
                    _pendingRequests.value = participantRepository.getPendingRequests(tripId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resolveRequest(
        userId: String,
        approve: Boolean,
    ) {
        screenModelScope.launch {
            participantRepository.resolveRequest(tripId, userId, approve)
            _pendingRequests.update { it.filter { req -> req.id != userId } }
        }
    }

    private fun regenerateCode() {
        screenModelScope.launch {
            tripRepository.regenerateCode(tripId)
        }
    }

    private fun archiveTrip(isArchived: Boolean) {
        screenModelScope.launch {
            val status = if (isArchived) "PLANNED" else "ARCHIVED"
            tripRepository.setTripStatus(tripId, status)
        }
    }

    private fun deleteOrLeaveTrip() {
        screenModelScope.launch {
            tripRepository.deleteOrLeaveTrip(tripId)
        }
    }

    private fun uploadFile(
        bytes: ByteArray,
        fileName: String,
    ) {
        screenModelScope.launch {
            try {
                withTimeout(10_000) {
                    globalSyncManager.networkState.first { it == NetworkState.ONLINE }
                }

                val url = tripRepository.uploadFile(bytes, fileName)
                val trip = tripRepository.getTripById(tripId).filterNotNull().first()
                val existingFiles =
                    try {
                        json.decodeFromString<List<EventFileDto>>(trip.filesJson ?: "[]")
                    } catch (e: Exception) {
                        emptyList()
                    }
                val ext = fileName.substringAfterLast(".", "").lowercase()
                val type =
                    if (ext in listOf("jpg", "jpeg", "png", "gif", "webp")) "PHOTO" else "DOCUMENT"
                val updatedFiles =
                    existingFiles + EventFileDto(name = fileName, url = url, type = type)
                val updatedJson = json.encodeToString(updatedFiles)
                tripRepository.updateTripFilesRemote(tripId, updatedJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleChecklistItem(itemId: String) {
        screenModelScope.launch {
            checklistRepository.toggleItem(tripId, itemId)
        }
    }
}
