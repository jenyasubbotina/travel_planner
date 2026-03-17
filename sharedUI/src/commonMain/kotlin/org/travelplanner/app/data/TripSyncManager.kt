package org.travelplanner.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.TripEvent
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.features.tripDetails.history.data.HistoryRepository
import org.travelplanner.app.features.tripDetails.more.checklist.data.ChecklistRepository
import kotlin.math.pow
import kotlin.random.Random

enum class NetworkState { CONNECTING, ONLINE, OFFLINE }

class GlobalSyncManager(
    private val userSession: UserSession,
    private val api: TripApiService,
    private val tripRepo: TripRepository,
    private val participantRepo: ParticipantRepository,
    private val expenseRepo: ExpenseRepository,
    private val eventRepo: EventRepository,
    private val historyRepository: HistoryRepository,
    private val checklistRepo: ChecklistRepository,
) {
    private val _networkState = MutableStateFlow(NetworkState.OFFLINE)
    val networkState = _networkState.asStateFlow()

    private val _retryCountdown = MutableStateFlow<Int?>(null)
    val retryCountdown = _retryCountdown.asStateFlow()

    private val _wsEvents = MutableSharedFlow<TripEvent>(extraBufferCapacity = 20)
    val wsEvents = _wsEvents.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var syncJob: Job? = null

    init {
        scope.launch {
            networkState.collect { state ->
                val isOffline = state != NetworkState.ONLINE
                api.isOffline = isOffline
                println("SYNC: Состояние сети -> $state (API isOffline = $isOffline)")
            }
        }

        scope.launch {
            userSession.currentUser
                .distinctUntilChangedBy { it?.id }
                .collect { user ->
                    syncJob?.cancel()
                    syncJob = null
                    if (user != null) {
                        println("User logged in: Starting WebSocket...")
                        syncJob = scope.launch { startGlobalSyncLoop() }
                    } else {
                        println("User logged out: Stopping WebSocket...")
                        _networkState.value = NetworkState.OFFLINE
                    }
                }
        }
    }

    private suspend fun startGlobalSyncLoop() {
        _networkState.value = NetworkState.CONNECTING
        _retryCountdown.value = null
        api
            .connectToGlobalEvents(
                onConnect = {
                    _networkState.value = NetworkState.ONLINE
                    _retryCountdown.value = null
                    println("WS-DEBUG: Connection restored!")
                },
            ).retryWhen { cause, attempt ->
                val baseDelay = (2000L * 2.0.pow(attempt.toDouble())).toLong()
                val cappedDelay = baseDelay.coerceAtMost(30_000L)
                val jitter = Random.nextLong(0, 1000)
                val finalDelay = cappedDelay + jitter
                println("WS-DEBUG: Disconnected (${cause.message}). Attempt ${attempt + 1}. Retrying in ${finalDelay / 1000}s...")
                _networkState.value = NetworkState.OFFLINE

                var remaining = (finalDelay / 1000).toInt()
                while (remaining > 0) {
                    _retryCountdown.value = remaining
                    delay(1000)
                    remaining--
                }
                _retryCountdown.value = null

                _networkState.value = NetworkState.CONNECTING
                true
            }.collect { event ->
                _wsEvents.tryEmit(event)
                handleWsEvent(event)
            }
    }

    fun markOnline() {
        _networkState.value = NetworkState.ONLINE
    }

    fun stopGlobalSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun onAppResumed() {
        if (_networkState.value == NetworkState.ONLINE ||
            _networkState.value == NetworkState.CONNECTING) {
            return
        }
        _retryCountdown.value = null
        syncJob?.cancel()
        syncJob = scope.launch { startGlobalSyncLoop() }
    }

    private suspend fun handleWsEvent(event: TripEvent) {
        val tripId = event.tripId
        val currentUserId =
            userSession.currentUser.value
                ?.id
                .toString()

        when (event) {
            is TripEvent.TripDeleted -> {
                tripRepo.deleteTripLocal(tripId)
            }

            is TripEvent.TripUpdated -> {
                tripRepo.updateTripBudgetLocal(
                    tripId,
                    event.trip.totalBudget,
                )
            }

            is TripEvent.ParticipantLeft -> {
                participantRepo.handleParticipantLeft(
                    tripId,
                    event.userId,
                )
            }

            is TripEvent.ParticipantJoined -> {
                participantRepo.insertOrUpdateParticipant(tripId, event.user)

                val myId =
                    userSession.currentUser.value
                        ?.id
                        .toString()
                if (event.user.id == myId) {
                    tripRepo.updateTripStatusLocal(tripId, "PLANNED")
                }
            }

            is TripEvent.ExpenseAdded -> {
                expenseRepo.upsertExpenseFromDto(tripId, event.expense)
            }

            is TripEvent.ExpenseUpdated -> {
                expenseRepo.upsertExpenseFromDto(tripId, event.expense)
            }

            is TripEvent.ExpenseDeleted -> {
                expenseRepo.deleteExpenseOnline(event.id, tripId)
            }

            is TripEvent.EventAdded -> {
                eventRepo.saveEventLocally(event.event)
            }

            is TripEvent.EventUpdated -> {
                eventRepo.saveEventLocally(event.event)
            }

            is TripEvent.EventDeleted -> {
                eventRepo.deleteEventByRemoteId(event.id)
            }

            is TripEvent.CodeRegenerated -> {
                tripRepo.updateJoinCodeLocal(tripId, event.newCode)
            }

            is TripEvent.JoinRequestReceived -> {
                println("WS: Join request received")
            }

            is TripEvent.JoinRequestResolved -> {
                val myId =
                    userSession.currentUser.value
                        ?.id
                        .toString()

                if (event.userId == myId && event.approved) {
                    println("WS: Join Approved! Switching to PLANNED.")
                    tripRepo.updateTripStatusLocal(tripId, "PLANNED")
                }
            }

            is TripEvent.HistoryAdded -> {
                historyRepository.saveLogLocally(event.log)
            }

            is TripEvent.ChecklistUpdated -> {
                if (event.item.isGroup || event.item.ownerUserId == currentUserId) {
                    checklistRepo.saveLocally(event.item)
                }
            }

            is TripEvent.ChecklistDeleted -> {
                checklistRepo.deleteLocally(event.id)
            }

            is TripEvent.TripFilesUpdated -> {
                tripRepo.updateTripFilesJson(tripId, event.filesJson)
            }
        }
    }
}
