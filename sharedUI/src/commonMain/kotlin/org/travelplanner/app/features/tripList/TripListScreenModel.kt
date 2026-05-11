package org.travelplanner.app.features.tripList

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.travelplanner.app.core.BackendApiException
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.VersionConflictException
import org.travelplanner.app.core.toEpochMillis
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.OutboxRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import kotlin.time.Clock

class TripListScreenModel(
    private val repository: TripRepository,
    private val userSession: UserSession,
    private val globalSyncManager: GlobalSyncManager,
    private val participantRepository: ParticipantRepository,
    private val outbox: OutboxRepository,
) : ReactiveScreenModel<TripListState, TripListIntent, TripListEffect>() {
    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow(TripFilter.ALL)
    private val _sortOrder = MutableStateFlow(SortOrder.DESC)

    val networkState = globalSyncManager.networkState

    init {
        syncTrips()
    }

    override val state: StateFlow<TripListState> =
        combine(
            combine(_searchQuery, _sortOrder, ::Pair),
            _activeFilter,
            repository.getTripsFlow(),
            outbox.observePendingCount(),
            participantRepository.getPendingInvitationsFlow(),
        ) { (query, sortOrder), filter, allTrips, pendingCount, pendingInvitations ->
            val searchedTrips =
                if (query.isEmpty()) {
                    allTrips
                } else {
                    allTrips.filter {
                        it.title.contains(query, ignoreCase = true) ||
                            it.destination.contains(query, ignoreCase = true)
                    }
                }

            val now = Clock.System.now().toEpochMilliseconds()

            val filteredTrips =
                when (filter) {
                    TripFilter.UPCOMING -> {
                        searchedTrips.filter {
                            it.status != "ARCHIVED" && (it.endDate?.toEpochMillis() ?: 0L) >= now
                        }
                    }

                    TripFilter.ARCHIVED -> {
                        searchedTrips.filter {
                            it.status == "ARCHIVED" || (it.endDate?.toEpochMillis() ?: 0L) < now
                        }
                    }

                    TripFilter.ALL -> {
                        searchedTrips
                    }
                }

            val sortedTrips =
                when (sortOrder) {
                    SortOrder.ASC -> filteredTrips.sortedBy { it.startDate?.toEpochMillis() ?: Long.MAX_VALUE }
                    SortOrder.DESC -> filteredTrips.sortedByDescending { it.startDate?.toEpochMillis() ?: Long.MIN_VALUE }
                }

            val pendingInvitationByTripId =
                pendingInvitations.associate { it.tripId to it.invitationId }

            TripListState(
                trips = sortedTrips,
                searchQuery = query,
                activeFilter = filter,
                sortOrder = sortOrder,
                pendingCount = pendingCount,
                pendingInvitationByTripId = pendingInvitationByTripId,
            )
        }.stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TripListState(),
        )

    override fun handleIntent(intent: TripListIntent) {
        when (intent) {
            is TripListIntent.Search -> {
                _searchQuery.value = intent.query
            }

            is TripListIntent.FilterChange -> {
                _activeFilter.value = intent.filter
            }

            is TripListIntent.RequestJoin -> {
                requestJoin(intent.code)
            }

            is TripListIntent.AcceptInvitation -> {
                registerInvitationLocally(intent.invitationId)
            }

            is TripListIntent.AcceptPendingInvitation -> {
                acceptPendingInvitation(intent.invitationId)
            }

            is TripListIntent.DeclinePendingInvitation -> {
                declinePendingInvitation(intent.invitationId)
            }

            is TripListIntent.Refresh -> {
                syncTrips()
            }

            is TripListIntent.DismissMessage -> {}

            is TripListIntent.ToggleSortOrder -> {
                _sortOrder.value = if (_sortOrder.value == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
            }
        }
    }

    private fun syncTrips() {
        screenModelScope.launch {
            val user = userSession.currentUser.value ?: return@launch
            try {
                val remoteTrips = repository.syncTripsFromServer()
                repository.saveSyncedTrips(remoteTrips)
            } catch (e: Exception) {
                e.printStackTrace()
                sendEffect(TripListEffect.ShowMessage("Ошибка соединения: не удалось загрузить поездки"))
            }
        }
    }

    private fun registerInvitationLocally(invitationId: String) {
        screenModelScope.launch {
            val trimmed = invitationId.trim()
            try {
                val invitations = participantRepository.fetchPendingInvitations()
                val match = invitations.firstOrNull { it.id == trimmed }
                if (match == null) {
                    sendEffect(TripListEffect.ShowMessage("Приглашение не найдено"))
                    return@launch
                }
                repository.savePendingInvitationPlaceholder(match)
                participantRepository.upsertPendingInvitation(match)
                sendEffect(TripListEffect.ShowMessage("Приглашение добавлено. Решите принять или отклонить."))
            } catch (e: BackendApiException) {
                println("[register-invite] ${e.statusCode} ${e.error.code}: ${e.error.message}")
                val msg =
                    when (e.statusCode) {
                        400, 422 -> "Неверный формат кода приглашения"
                        403 -> "Приглашение отправлено на другой email"
                        404 -> "Приглашение не найдено"
                        else -> "Не удалось загрузить приглашение (${e.statusCode})"
                    }
                sendEffect(TripListEffect.ShowMessage(msg))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[register-invite] unexpected: ${e::class.simpleName}: ${e.message}")
                sendEffect(TripListEffect.ShowMessage("Не удалось загрузить приглашение: ${e.message ?: e::class.simpleName}"))
            }
        }
    }

    private fun acceptPendingInvitation(invitationId: String) {
        screenModelScope.launch {
            try {
                participantRepository.acceptInvitation(invitationId)
                globalSyncManager.forceFullRefresh()
                sendEffect(TripListEffect.ShowMessage("Вы добавлены в поездку"))
            } catch (e: VersionConflictException) {
                sendEffect(TripListEffect.ShowMessage("Приглашение уже использовано"))
            } catch (e: BackendApiException) {
                println("[accept] ${e.statusCode} ${e.error.code}: ${e.error.message}")
                val msg =
                    when (e.statusCode) {
                        400, 422 -> "Неверный формат кода приглашения"
                        403 -> "Приглашение отправлено на другой email"
                        404 -> "Приглашение не найдено"
                        else -> "Не удалось принять приглашение (${e.statusCode})"
                    }
                sendEffect(TripListEffect.ShowMessage(msg))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[accept] unexpected: ${e::class.simpleName}: ${e.message}")
                sendEffect(TripListEffect.ShowMessage("Не удалось принять приглашение: ${e.message ?: e::class.simpleName}"))
            }
        }
    }

    private fun declinePendingInvitation(invitationId: String) {
        screenModelScope.launch {
            try {
                participantRepository.declineInvitation(invitationId)
                sendEffect(TripListEffect.ShowMessage("Приглашение отклонено"))
            } catch (e: BackendApiException) {
                println("[decline] ${e.statusCode} ${e.error.code}: ${e.error.message}")
                val msg =
                    when (e.statusCode) {
                        403 -> "Приглашение отправлено на другой email"
                        404 -> "Приглашение не найдено"
                        else -> "Не удалось отклонить приглашение (${e.statusCode})"
                    }
                sendEffect(TripListEffect.ShowMessage(msg))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[decline] unexpected: ${e::class.simpleName}: ${e.message}")
                sendEffect(TripListEffect.ShowMessage("Не удалось отклонить приглашение: ${e.message ?: e::class.simpleName}"))
            }
        }
    }

    private fun requestJoin(code: String) {
        screenModelScope.launch {
            val currentUser = userSession.currentUser.value
            if (currentUser == null) {
                sendEffect(TripListEffect.ShowMessage("Ошибка: Вы не авторизованы"))
                return@launch
            }

            try {
                val tripResponse =
                    repository.requestJoinTrip(
                        code = code,
                        userId = currentUser.id,
                        name = currentUser.name,
                    )

                if (tripResponse != null) {
                    sendEffect(TripListEffect.ShowMessage("Заявка отправлена. Ожидайте подтверждения."))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendEffect(TripListEffect.ShowMessage("Ошибка при отправке заявки: проверьте код"))
            }
        }
    }
}
