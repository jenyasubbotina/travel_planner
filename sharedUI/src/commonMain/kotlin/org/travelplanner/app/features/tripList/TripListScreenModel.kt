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

    val networkState = globalSyncManager.networkState

    init {
        syncTrips()
    }

    override val state: StateFlow<TripListState> =
        combine(
            _searchQuery,
            _activeFilter,
            repository.getTripsFlow(),
            outbox.observePendingCount(),
        ) { query, filter, allTrips, pendingCount ->
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
                    TripFilter.UPCOMING -> searchedTrips.filter { it.status != "ARCHIVED" && (it.endDate?.toEpochMillis() ?: 0L) >= now }
                    TripFilter.ARCHIVED -> searchedTrips.filter { it.status == "ARCHIVED" || (it.endDate?.toEpochMillis() ?: 0L) < now }
                    TripFilter.ALL -> searchedTrips
                }

            TripListState(
                trips = filteredTrips,
                searchQuery = query,
                activeFilter = filter,
                pendingCount = pendingCount,
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
                acceptInvitation(intent.invitationId)
            }

            is TripListIntent.Refresh -> {
                syncTrips()
            }

            is TripListIntent.DismissMessage -> {}
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

    private fun acceptInvitation(invitationId: String) {
        screenModelScope.launch {
            try {
                participantRepository.acceptInvitation(invitationId.trim())
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
                    syncTrips()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendEffect(TripListEffect.ShowMessage("Ошибка при отправке заявки: проверьте код"))
            }
        }
    }
}
