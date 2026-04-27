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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.DSEmptyStateCard
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.features.tripDetails.history.data.HistoryDiffUtils
import org.travelplanner.app.features.tripDetails.history.data.HistoryRepository
import org.travelplanner.app.theme.DSLoadingOverlay

data class HistoryItemUiModel(
    val logId: String,
    val userId: String,
    val userName: String,
    val avatarUrl: String?,
    val entityType: String,
    val actionType: String,
    val actionText: String,
    val timestamp: Long,
    val parsedChanges: List<String>,
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val logs: List<HistoryItemUiModel> = emptyList(),
)

class HistoryScreenModel(
    private val tripId: String,
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
                    val payload = HistoryDiffUtils.parsePayload(log.details)
                    val expenseCategory =
                        payload
                            ?.get("entity")
                            ?.jsonObject
                            ?.get("category")
                            ?.jsonPrimitive
                            ?.content

                    HistoryItemUiModel(
                        logId = log.id,
                        userId = log.userId,
                        userName = user?.name ?: "Неизвестный пользователь",
                        avatarUrl = user?.avatarUrl,
                        entityType = log.entityType,
                        actionType = log.actionType,
                        actionText = actionPhraseFor(log.entityType, log.actionType, expenseCategory),
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

private fun actionPhraseFor(entityType: String, actionType: String, expenseCategory: String?): String =
    when (entityType to actionType) {
        "EXPENSE" to "CREATE" -> if (expenseCategory == "PAYMENT") "вернул(а) долг" else "добавил(а) новый расход"
        "EXPENSE" to "UPDATE" -> "изменил(а) расход"
        "EXPENSE" to "DELETE" -> "удалил(а) расход"
        "EXPENSE" to "STORE_PENDING_UPDATE" -> "предложил(а) изменения в расходе"
        "EXPENSE" to "REJECT_PENDING_UPDATE" -> "отклонил(а) изменения в расходе"
        "EVENT" to "CREATE" -> "добавил(а) событие в маршрут"
        "EVENT" to "UPDATE" -> "изменил(а) событие"
        "EVENT" to "DELETE" -> "удалил(а) событие"
        "TRIP" to "CREATE" -> "создал(а) поездку"
        "TRIP" to "UPDATE" -> "изменил(а) настройки поездки"
        "TRIP" to "DELETE" -> "удалил(а) поездку"
        "TRIP" to "ARCHIVE" -> "архивировал(а) поездку"
        "TRIP" to "REGENERATE_JOIN_CODE" -> "обновил(а) код приглашения"
        "TRIP" to "REORDER_ITINERARY" -> "переупорядочил(а) маршрут"
        "CHECKLIST_ITEM" to "CREATE" -> "добавил(а) пункт чек-листа"
        "CHECKLIST_ITEM" to "DELETE" -> "удалил(а) пункт чек-листа"
        "CHECKLIST_ITEM" to "COMPLETE" -> "отметил(а) пункт чек-листа"
        "CHECKLIST_ITEM" to "UNCOMPLETE" -> "снял(а) отметку с пункта"
        "PARTICIPANT" to "INVITE" -> "пригласил(а) участника"
        "PARTICIPANT" to "JOIN" -> "присоединился(-ась) к поездке"
        "PARTICIPANT" to "DELETE" -> "удалил(а) участника"
        "PARTICIPANT" to "CHANGE_ROLE" -> "изменил(а) роль участника"
        "JOIN_REQUEST" to "REQUEST_JOIN" -> "запросил(а) присоединение по коду"
        "JOIN_REQUEST" to "APPROVE_JOIN" -> "одобрил(а) запрос на присоединение"
        "JOIN_REQUEST" to "DENY_JOIN" -> "отклонил(а) запрос на присоединение"
        "LINK" to "CREATE" -> "добавил(а) ссылку к событию"
        "LINK" to "DELETE" -> "удалил(а) ссылку у события"
        "COMMENT" to "CREATE" -> "оставил(а) комментарий к событию"
        "ATTACHMENT" to "CREATE" -> "прикрепил(а) файл"
        "ATTACHMENT" to "DELETE" -> "удалил(а) файл"
        else -> "внес(ла) изменения"
    }

data class HistoryScreen(
    val tripId: String,
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
