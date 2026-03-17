package org.travelplanner.app.features.tripDetails.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.DSEmptyStateCard
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.features.tripDetails.history.data.HistoryDiffUtils
import org.travelplanner.app.features.tripDetails.history.data.HistoryRepository
import org.travelplanner.app.theme.DSLoadingOverlay

data class HistoryItemUiModel(
    val logId: String,
    val userName: String,
    val avatarColor: String,
    val actionText: String,
    val timestamp: Long,
    val parsedChanges: List<String>,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val logs: List<HistoryItemUiModel> = emptyList(),
)

class HistoryScreenModel(
    private val tripId: Long,
    private val historyRepository: HistoryRepository,
    private val participantRepository: ParticipantRepository,
) : ScreenModel {
    val state =
        combine(
            historyRepository.getLogsFlow(tripId),
            participantRepository.getParticipantsFlow(tripId),
        ) { logs, participants ->

            val uiLogs =
                logs.map { log ->
                    val user = participants.find { it.userId == log.userId }

                    val actionPhrase =
                        when (log.entityType) {
                            "EXPENSE" if log.actionType == "CREATE" && log.details.contains("PAYMENT") -> "вернул(а) долг"
                            "EXPENSE" if log.actionType == "CREATE" -> "добавил(а) новый расход"
                            "EXPENSE" if log.actionType == "UPDATE" -> "изменил(а) расход"
                            "EXPENSE" if log.actionType == "DELETE" -> "удалил(а) расход"
                            "EVENT" if log.actionType == "CREATE" -> "добавил(а) событие в маршрут"
                            "EVENT" if log.actionType == "UPDATE" -> "изменил(а) событие"
                            "EVENT" if log.actionType == "DELETE" -> "удалил(а) событие"
                            "PARTICIPANT" if log.actionType == "CREATE" -> "присоединился(-ась) к поездке"
                            "PARTICIPANT" if log.actionType == "DELETE" -> "покинул(а) поездку"
                            "TRIP" if log.actionType == "UPDATE" -> "изменил(а) настройки поездки"
                            else -> "внес(ла) изменения"
                        }

                    HistoryItemUiModel(
                        logId = log.id,
                        userName = user?.name ?: "Unknown User",
                        avatarColor = user?.avatarColor1 ?: "#CCCCCC",
                        actionText = actionPhrase,
                        timestamp = log.timestamp,
                        parsedChanges =
                            HistoryDiffUtils.generateDiffText(
                                actionType = log.actionType,
                                entityType = log.entityType,
                                rawJson = log.details,
                                participants = participants,
                            ),
                    )
                }

            HistoryUiState(isLoading = false, logs = uiLogs)
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    init {
        screenModelScope.launch { historyRepository.syncHistory(tripId) }
    }
}

data class HistoryScreen(
    val tripId: Long,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<HistoryScreenModel> { parametersOf(tripId) }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "История активности",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors =
                        TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color(0xFFF9FAFB),
                        ),
                )
            },
            containerColor = Color(0xFFF9FAFB),
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (state.isLoading) {
                    DSLoadingOverlay(modifier = Modifier.align(Alignment.Center))
                } else if (state.logs.isEmpty()) {
                    DSEmptyStateCard(
                        title = "История пуста",
                        description = "Здесь будут отображаться все изменения в поездке",
                        buttonText = "Вернуться",
                        onButtonClick = { navigator.pop() },
                        icon = Icons.Default.Schedule,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.logs) { log ->
                            HistoryCard(log)
                        }
                    }
                }
            }
        }
    }
}
