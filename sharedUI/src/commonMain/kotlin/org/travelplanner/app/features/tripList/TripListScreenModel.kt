package org.travelplanner.app.features.tripList

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.TripEvent
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.TripRepository
import kotlin.time.Clock

class TripListScreenModel(
    private val repository: TripRepository,
    private val userSession: UserSession,
    private val globalSyncManager: GlobalSyncManager,
) : ReactiveScreenModel<TripListState, TripListIntent, TripListEffect>() {
    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow(TripFilter.ALL)

    init {
        syncTrips()
    }

    override val state: StateFlow<TripListState> =
        combine(
            _searchQuery,
            _activeFilter,
            repository.getTripsFlow(),
        ) { query, filter, allTrips ->
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
                    TripFilter.UPCOMING -> searchedTrips.filter { it.status != "ARCHIVED" && it.endDate >= now }
                    TripFilter.ARCHIVED -> searchedTrips.filter { it.status == "ARCHIVED" || it.endDate < now }
                    TripFilter.ALL -> searchedTrips
                }

            TripListState(
                trips = filteredTrips,
                searchQuery = query,
                activeFilter = filter,
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

    fun resolveUrl(path: String?): String? = repository.resolveUrl(path)

    private fun requestJoin(code: String) {
        screenModelScope.launch {
            val currentUser = userSession.currentUser.value
            if (currentUser == null) {
                sendEffect(TripListEffect.ShowMessage("Ошибка: Вы не авторизованы"))
                return@launch
            }

            try {
                val eventDeferred =
                    async {
                        globalSyncManager.wsEvents.first {
                            it is TripEvent.JoinRequestResolved && it.userId == currentUser.id.toString()
                        } as TripEvent.JoinRequestResolved
                    }

                val trip =
                    repository.requestJoinTrip(
                        code = code,
                        userId = currentUser.id.toString(),
                        name = currentUser.name,
                    )

                repository.insertPendingTrip(trip)
                sendEffect(TripListEffect.ShowMessage("Заявка отправлена. Ожидайте подтверждения."))

                launch {
                    try {
                        val event = eventDeferred.await()
                        if (event.approved) {
                            sendEffect(TripListEffect.ShowMessage("Заявка одобрена! Поездка добавлена."))
                            syncTrips()
                        } else {
                            sendEffect(TripListEffect.ShowMessage("К сожалению, ваша заявка была отклонена."))
                            repository.deleteTripLocal(trip.id)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                sendEffect(TripListEffect.ShowMessage("Ошибка при отправке заявки: проверьте код"))
            }
        }
    }
}
