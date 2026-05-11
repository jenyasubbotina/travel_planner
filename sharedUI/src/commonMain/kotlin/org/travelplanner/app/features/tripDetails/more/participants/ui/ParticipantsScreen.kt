package org.travelplanner.app.features.tripDetails.more.participants.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.core.InvitationResponse
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.domain.PendingUser
import org.travelplanner.app.features.tripDetails.more.ParticipantRowDetailed

data class ParticipantsState(
    val participants: List<Participant> = emptyList(),
    val pendingRequests: List<PendingUser> = emptyList(),
    val pendingInvitations: List<InvitationResponse> = emptyList(),
    val isOwner: Boolean = false,
)

class ParticipantsScreenModel(
    private val tripId: String,
    private val participantRepository: ParticipantRepository,
    private val tripRepository: TripRepository,
    private val userSession: UserSession,
) : ScreenModel {
    private val isOwnerFlow =
        tripRepository
            .getTripById(tripId)
            .filterNotNull()
            .map { trip -> trip.ownerUserId == userSession.currentUser.value?.id }

    private val _pendingInvitations = MutableStateFlow<List<InvitationResponse>>(emptyList())

    val state =
        combine(
            participantRepository.getParticipantsFlow(tripId),
            participantRepository.getPendingRequestsFlow(tripId),
            _pendingInvitations,
            isOwnerFlow,
        ) { participants, pending, invitations, isOwner ->
            ParticipantsState(
                participants = participants,
                pendingRequests = pending,
                pendingInvitations = invitations,
                isOwner = isOwner,
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ParticipantsState())

    init {
        screenModelScope.launch {
            try {
                participantRepository.syncParticipants(tripId)
            } catch (_: Exception) {
            }
        }
        refreshPendingInvitations()
    }

    private fun refreshPendingInvitations() {
        screenModelScope.launch {
            try {
                _pendingInvitations.value =
                    participantRepository.fetchTripPendingInvitations(tripId)
            } catch (_: Exception) {
            }
        }
    }

    fun resolveRequest(
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class ParticipantsScreen(
    val tripId: String,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<ParticipantsScreenModel> { parametersOf(tripId) }
        val state by screenModel.state.collectAsState()

        var declineTarget by remember { mutableStateOf<PendingUser?>(null) }

        declineTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { declineTarget = null },
                title = { Text("Отклонить заявку?") },
                text = {
                    Text(
                        "Пользователь ${target.name} не сможет повторно отправить заявку " +
                            "без нового приглашения.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.resolveRequest(target.id, false)
                        declineTarget = null
                    }) { Text("Отклонить", color = Color(0xFFEF4444)) }
                },
                dismissButton = {
                    TextButton(onClick = { declineTarget = null }) { Text("Отмена") }
                },
                containerColor = Color.White,
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Участники", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        state.participants.sortedBy { it.role == "LEFT" }.forEach { participant ->
                            ParticipantRowDetailed(
                                name = participant.name,
                                email = participant.email,
                                role = participant.role,
                                userId = participant.userId,
                                avatarUrl = participant.avatarUrl,
                            )
                        }
                    }
                }

                item {
                    if (state.pendingRequests.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Divider(
                                color = Color(0xFFF3F4F6),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Text(
                                "ЗАЯВКИ НА ВСТУПЛЕНИЕ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B),
                            )
                            state.pendingRequests.forEach { req ->
                                ParticipantRowDetailed(
                                    name = req.name,
                                    email = req.email,
                                    isPending = true,
                                    onAccept =
                                        if (state.isOwner) {
                                            { screenModel.resolveRequest(req.id, true) }
                                        } else {
                                            null
                                        },
                                    onDecline =
                                        if (state.isOwner) {
                                            { declineTarget = req }
                                        } else {
                                            null
                                        },
                                )
                            }
                        }
                    }
                }

                item {
                    if (state.pendingInvitations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Divider(
                                color = Color(0xFFF3F4F6),
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                            Text(
                                "ПРИГЛАШЕНИЯ ОТПРАВЛЕНЫ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B),
                            )
                            state.pendingInvitations.forEach { invite ->
                                ParticipantRowDetailed(
                                    name = invite.email,
                                    email = "Приглашение • ${invite.role}",
                                    isPending = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
