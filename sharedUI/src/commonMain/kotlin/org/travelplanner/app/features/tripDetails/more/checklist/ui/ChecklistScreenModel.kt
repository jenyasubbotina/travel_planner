package org.travelplanner.app.features.tripDetails.more.checklist.ui

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.features.tripDetails.more.checklist.data.ChecklistRepository

class ChecklistScreenModel(
    private val tripId: String,
    private val checklistRepository: ChecklistRepository,
    private val participantRepository: ParticipantRepository,
    private val userSession: UserSession,
) : ReactiveScreenModel<ChecklistState, ChecklistIntent, ChecklistEffect>() {
    private val currentUserId =
        userSession.currentUser.value
            ?.id ?: ""

    override val state: StateFlow<ChecklistState> =
        combine(
            checklistRepository.getChecklistFlow(tripId).map { list ->
                list.filter { it.isGroup || it.ownerUserId == currentUserId }
            },
            participantRepository.getParticipantsFlow(tripId),
        ) { checklist, participants ->
            ChecklistState(
                items = checklist,
                totalParticipants = participants.size,
                currentUserId = currentUserId,
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ChecklistState())

    override fun handleIntent(intent: ChecklistIntent) {
        when (intent) {
            is ChecklistIntent.AddItem -> {
                if (intent.title.isBlank()) return
                screenModelScope.launch {
                    checklistRepository.addItem(tripId, intent.title, intent.isGroup)
                }
            }

            is ChecklistIntent.ToggleItem -> {
                screenModelScope.launch {
                    checklistRepository.toggleItem(tripId, intent.itemId)
                }
            }

            is ChecklistIntent.DeleteItem -> {
                screenModelScope.launch {
                    checklistRepository.deleteItem(tripId, intent.itemId)
                }
            }
        }
    }
}
