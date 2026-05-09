package org.travelplanner.app.features.tripDetails.more

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.travelplanner.app.core.BackendApiException
import org.travelplanner.app.core.GlobalNotifier
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.VersionConflictException
import org.travelplanner.app.core.extractBackendS3KeyOrNull
import org.travelplanner.app.core.toEpochMillis
import org.travelplanner.app.data.EventRepository
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.MediaType
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.domain.PendingUser
import org.travelplanner.app.domain.TripMediaItem
import org.travelplanner.app.features.tripDetails.more.checklist.data.ChecklistRepository

class MoreTabScreenModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val eventRepository: EventRepository,
    private val checklistRepository: ChecklistRepository,
    private val globalSyncManager: GlobalSyncManager,
    private val userSession: UserSession,
    private val globalNotifier: GlobalNotifier,
    private val json: Json,
) : ReactiveScreenModel<MoreState, MoreTabIntent, MoreTabEffect>() {
    private val _isAddDialogVisible = MutableStateFlow(false)
    private val _isInviteEmailDialogVisible = MutableStateFlow(false)
    private val _isInviteInFlight = MutableStateFlow(false)

    private val _lastCreatedInvitationId = MutableStateFlow<String?>(null)

    val currentUser = userSession.currentUser.value

    private val mediaItemsFlow =
        combine(
            tripRepository.getTripById(tripId).filterNotNull(),
            expenseRepository.getExpensesFlow(tripId),
            eventRepository.getEventsFlow(tripId),
            tripRepository.getTripLevelAttachmentsFlow(tripId),
        ) { trip, expenses, events, tripAttachments ->
            val files = mutableListOf<TripMediaItem>()

            val tripStartMillis = trip.startDate?.toEpochMillis() ?: 0L

            trip.imageUrl?.let {
                files.add(
                    TripMediaItem(
                        url = it,
                        title = "Обложка",
                        subtitle = trip.title,
                        type = MediaType.IMAGE,
                        date = tripStartMillis,
                        category = "Поездка",
                        s3Key = it,
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
                            exp.date.toEpochMillis(),
                            "Расходы",
                        ),
                    )
                }
            }

            events.forEach { ev ->
                val eventDate = tripStartMillis + (ev.dayIndex * 86400000L)

                ev.files.forEach { file ->
                    files.add(
                        TripMediaItem(
                            url = file.url,
                            title = file.name,
                            subtitle = ev.title,
                            type = if (file.type == "PHOTO") MediaType.IMAGE else MediaType.DOCUMENT,
                            date = eventDate,
                            category = "События",
                            s3Key = file.url,
                        ),
                    )
                }
            }

            tripAttachments.forEach { att ->
                val isImage = att.mimeType.startsWith("image/")
                files.add(
                    TripMediaItem(
                        url = att.s3Key,
                        title = att.fileName,
                        subtitle = "Загруженный файл",
                        type = if (isImage) MediaType.IMAGE else MediaType.DOCUMENT,
                        date =
                            runCatching { att.createdAt.toEpochMillis() }.getOrNull()
                                ?: tripStartMillis,
                        category = "Файлы",
                        s3Key = att.s3Key,
                    ),
                )
            }

            files.sortedByDescending { it.date }
        }

    private val managementFlow =
        combine(
            participantRepository.getParticipantsFlow(tripId),
            participantRepository.getPendingRequestsFlow(tripId),
            _isAddDialogVisible,
            userSession.currentUser,
        ) { participants, pending, showDialog, user ->
            ManagementState(participants, pending, showDialog, user)
        }

    private val inviteFlow =
        combine(
            _isInviteEmailDialogVisible,
            _isInviteInFlight,
            _lastCreatedInvitationId,
        ) { visible, inFlight, createdId ->
            InviteState(visible, inFlight, createdId)
        }

    override val state: StateFlow<MoreState> =
        combine(
            tripRepository.getTripById(tripId).filterNotNull(),
            mediaItemsFlow,
            managementFlow,
            inviteFlow,
            checklistRepository.getChecklistFlow(tripId).map { list ->
                list.filter { it.isGroup || it.ownerUserId == currentUser?.id }
            },
        ) { trip, media, mgmt, invite, checklist ->
            MoreState(
                trip = trip,
                list = mgmt.participants,
                pendingRequests = mgmt.pending,
                isAddDialogVisible = mgmt.showDialog,
                currentUser = mgmt.user,
                mediaItems = media,
                checklist = checklist,
                isInviteEmailDialogVisible = invite.visible,
                isInviteInFlight = invite.inFlight,
                lastCreatedInvitationId = invite.createdInvitationId,
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), MoreState())

    private data class ManagementState(
        val participants: List<Participant>,
        val pending: List<PendingUser>,
        val showDialog: Boolean,
        val user: org.travelplanner.app.core.AppUser?,
    )

    private data class InviteState(
        val visible: Boolean,
        val inFlight: Boolean,
        val createdInvitationId: String?,
    )

    init {
        screenModelScope.launch {
            try {
                participantRepository.syncParticipants(tripId)
            } catch (_: Exception) {
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

            is MoreTabIntent.ShowInviteEmailDialog -> {
                _lastCreatedInvitationId.value = null
                _isInviteEmailDialogVisible.value = true
            }

            is MoreTabIntent.HideInviteEmailDialog -> {
                _isInviteEmailDialogVisible.value = false
                _lastCreatedInvitationId.value = null
            }

            is MoreTabIntent.InviteByEmail -> {
                inviteByEmail(intent.email, intent.role)
            }

            is MoreTabIntent.AcknowledgeInvitationShared -> {
                _isInviteEmailDialogVisible.value = false
                _lastCreatedInvitationId.value = null
            }

            is MoreTabIntent.DismissMessage -> {}
        }
    }

    private fun inviteByEmail(
        email: String,
        role: String,
    ) {
        screenModelScope.launch {
            _isInviteInFlight.value = true
            try {
                val invitation = participantRepository.inviteByEmail(tripId, email, role)

                _lastCreatedInvitationId.value = invitation.id
                globalNotifier.notifySuccess("Приглашение создано")
                participantRepository.syncParticipants(tripId)
            } catch (e: VersionConflictException) {
                val msg =
                    when (e.error.code) {
                        "ALREADY_PARTICIPANT" -> "Пользователь уже в поездке"
                        else -> "Приглашение уже отправлено этому пользователю"
                    }
                globalNotifier.notifyError(msg)
            } catch (e: BackendApiException) {
                println("[invite] ${e.statusCode} ${e.error.code}: ${e.error.message}")
                val msg =
                    when (e.statusCode) {
                        400 -> "Неверный email"
                        403 -> "Только владелец поездки может приглашать"
                        404 -> "Поездка не найдена"
                        422 -> e.error.message.ifBlank { "Приглашение уже отправлено или поездка не активна" }
                        else -> "Не удалось отправить приглашение (${e.statusCode})"
                    }
                globalNotifier.notifyError(msg)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[invite] unexpected: ${e::class.simpleName}: ${e.message}")
                globalNotifier.notifyError("Не удалось отправить приглашение")
            } finally {
                _isInviteInFlight.value = false
            }
        }
    }

    fun resolveUrl(path: String?): String? = path

    private fun resolveRequest(
        userId: String,
        approve: Boolean,
    ) {
        screenModelScope.launch {
            try {
                participantRepository.resolveRequest(tripId, userId, approve)
                if (approve) {
                    try {
                        participantRepository.syncParticipants(tripId)
                    } catch (_: Exception) {
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[resolveRequest] ${e::class.simpleName}: ${e.message}")
                globalNotifier.notifyError("Не удалось обработать заявку")
            }
        }
    }

    private fun regenerateCode() {
        screenModelScope.launch {
            try {
                tripRepository.regenerateCode(tripId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[regenerateCode] ${e::class.simpleName}: ${e.message}")
                globalNotifier.notifyError("Не удалось обновить код")
            }
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
                tripRepository.enqueueTripFileAttachment(tripId, bytes, fileName)
                tripRepository.refreshTripFiles(tripId)
                globalSyncManager.syncNow()
                globalNotifier.notifySuccess("Файл успешно загружен")
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep local placeholder as a fallback so user data is not silently lost.
                tripRepository.saveFakeAttachmentLocally(
                    tripId = tripId,
                    fileName = fileName,
                    fileSize = bytes.size.toLong(),
                )
                globalNotifier.notifyError("Не удалось загрузить файл на сервер, сохранена локальная копия")
            }
        }
    }

    suspend fun getDownloadUrl(item: TripMediaItem): String? {
        val raw = item.s3Key ?: item.url
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (raw.startsWith("local://")) {
            globalNotifier.notifyError("Этот файл сохранен только локально и недоступен для серверного скачивания")
            return null
        }
        val key =
            extractBackendS3KeyOrNull(raw)
                ?: run {
                    globalNotifier.notifyError("Не удалось скачать файл: неверный ключ в данных вложения")
                    return null
                }
        return try {
            tripRepository.getDownloadUrl(key)
        } catch (e: Exception) {
            e.printStackTrace()
            globalNotifier.notifyError("Не удалось получить ссылку для скачивания")
            null
        }
    }

    private fun toggleChecklistItem(itemId: String) {
        screenModelScope.launch {
            checklistRepository.toggleItem(tripId, itemId)
        }
    }
}
