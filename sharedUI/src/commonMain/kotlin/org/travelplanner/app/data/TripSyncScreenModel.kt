package org.travelplanner.app.data

import androidx.compose.runtime.mutableStateListOf
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.tab.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.travelplanner.app.core.BaseScreenModel
import org.travelplanner.app.features.tripDetails.more.checklist.data.ChecklistRepository

enum class SyncState { SYNCING, UP_TO_DATE, ERROR }

class TripDetailsScreenModel(
    private val tripId: String,
    private val participantRepo: ParticipantRepository,
    private val eventRepo: EventRepository,
    private val expenseRepo: ExpenseRepository,
    private val checklistRepository: ChecklistRepository,
    private val tripRepo: TripRepository,
    private val outbox: OutboxRepository,
    private val globalSyncManager: GlobalSyncManager,
) : BaseScreenModel<TripDetailsSyncState, TripDetailsIntent, TripDetailsEffect>(TripDetailsSyncState()) {
    val networkState = globalSyncManager.networkState
    val retryCountdown = globalSyncManager.retryCountdown

    private var activeSyncJob: Job? = null

    val tabHistory = mutableStateListOf<Tab>()

    init {
        screenModelScope.launch {
            networkState.collect { state ->
                if (state == NetworkState.ONLINE) {
                    handleIntent(TripDetailsIntent.PerformSync)
                }
            }
        }

        screenModelScope.launch {
            tripRepo.getTripById(tripId).collect { trip ->
                if (trip == null) {
                    sendEffect(TripDetailsEffect.KickUser)
                } else {
                    updateState { copy(trip = trip) }
                }
            }
        }

        screenModelScope.launch {
            outbox.observePendingCount(tripId).collect { count ->
                updateState { copy(pendingCount = count) }
            }
        }
        screenModelScope.launch {
            outbox.observeConflicts(tripId).collect { entries ->
                updateState { copy(conflicts = entries) }
            }
        }
        screenModelScope.launch {
            outbox.observeDead(tripId).collect { entries ->
                updateState { copy(deadEntries = entries) }
            }
        }
        screenModelScope.launch {
            outbox.observeDepthAlert(tripId).collect { tripped ->
                updateState { copy(depthAlert = tripped) }
            }
        }
    }

    override fun handleIntent(intent: TripDetailsIntent) {
        when (intent) {
            is TripDetailsIntent.PerformSync -> {
                performRestSync()
            }

            is TripDetailsIntent.DiscardDeadEntry -> {
                outbox.deleteEntry(intent.entryId)
            }

            is TripDetailsIntent.RetryEntry -> {
                outbox.markRetry(intent.entryId)
                globalSyncManager.syncNow()
            }
        }
    }

    private fun performRestSync() {
        if (activeSyncJob?.isActive == true) return

        activeSyncJob =
            screenModelScope.launch {
                updateState { copy(syncState = SyncState.SYNCING) }
                val failures = mutableListOf<Throwable>()
                joinAll(
                    launch { runSyncStep(failures) { participantRepo.syncParticipants(tripId) } },
                    launch { runSyncStep(failures) { eventRepo.syncEvents(tripId) } },
                    launch { runSyncStep(failures) { expenseRepo.syncExpenses(tripId) } },
                    launch { runSyncStep(failures) { checklistRepository.syncChecklist(tripId) } },
                )
                updateState {
                    copy(syncState = if (failures.isEmpty()) SyncState.UP_TO_DATE else SyncState.ERROR)
                }
            }
    }

    private suspend inline fun runSyncStep(
        failures: MutableList<Throwable>,
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            failures += e
        }
    }
}
