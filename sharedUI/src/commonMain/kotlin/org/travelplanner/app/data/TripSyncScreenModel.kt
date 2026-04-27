package org.travelplanner.app.data

import androidx.compose.runtime.mutableStateListOf
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.tab.Tab
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
    globalSyncManager: GlobalSyncManager,
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
    }

    override fun handleIntent(intent: TripDetailsIntent) {
        when (intent) {
            is TripDetailsIntent.PerformSync -> performRestSync()
        }
    }

    private fun performRestSync() {
        if (activeSyncJob?.isActive == true) return

        activeSyncJob =
            screenModelScope.launch {
                updateState { copy(syncState = SyncState.SYNCING) }
                try {
                    joinAll(
                        launch { participantRepo.syncParticipants(tripId) },
                        launch { eventRepo.syncEvents(tripId) },
                        launch { expenseRepo.syncExpenses(tripId) },
                        launch { checklistRepository.syncChecklist(tripId) },
                    )
                    updateState { copy(syncState = SyncState.UP_TO_DATE) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateState { copy(syncState = SyncState.ERROR) }
                }
            }
    }
}
