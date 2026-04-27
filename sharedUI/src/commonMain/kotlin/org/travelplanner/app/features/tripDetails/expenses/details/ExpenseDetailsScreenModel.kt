package org.travelplanner.app.features.tripDetails.expenses.details

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.travelplanner.app.HistoryLogEntity
import org.travelplanner.app.core.AppUser
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.TripUtils.formatDate
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.ExpenseSplit
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.features.tripDetails.expenses.domain.ExpenseLogParser
import org.travelplanner.app.features.tripDetails.history.data.HistoryRepository

class ExpenseDetailsScreenModel(
    private val expenseId: Long,
    private val tripId: String,
    private val expenseRepository: ExpenseRepository,
    private val participantRepository: ParticipantRepository,
    private val historyRepository: HistoryRepository,
    private val userSession: UserSession,
    private val tripRepository: TripRepository,
) : ReactiveScreenModel<ExpenseDetailsUiState, ExpenseDetailsIntent, ExpenseDetailsEffect>() {
    private val _isDeleted = MutableStateFlow(false)

    override val state: StateFlow<ExpenseDetailsUiState> =
        combine(
            expenseRepository.getExpenseById(expenseId),
            expenseRepository.getExpenseSplitsFlow(expenseId),
            historyRepository.getLogsFlow(tripId),
            participantRepository.getParticipantsFlow(tripId),
            userSession.currentUser,
            _isDeleted,
            tripRepository.getTripById(tripId),
        ) { flows ->
            val expense = flows[0] as Expense?
            val splits = flows[1] as List<ExpenseSplit>
            val logs = flows[2] as List<HistoryLogEntity>
            val participants = flows[3] as List<Participant>
            val currentUser = flows[4] as AppUser?
            val deleted = flows[5] as Boolean
            val trip = flows[6] as org.travelplanner.app.domain.Trip?
            val currency = trip?.currency ?: "¥"

            when {
                deleted -> {
                    ExpenseDetailsUiState.Deleted
                }

                expense == null -> {
                    ExpenseDetailsUiState.Error
                }

                else -> {
                    val myUserId = currentUser?.id
                    val expenseLogs =
                        logs
                            .filter { it.entityId == expense.remoteId && it.entityType == "EXPENSE" }
                            .sortedByDescending { it.timestamp }

                    val historyUiModels =
                        expenseLogs.mapIndexed { index, log ->
                            val user = participants.find { it.userId == log.userId }
                            val actorName =
                                if (log.userId == myUserId) "Вы" else (user?.name ?: "Неизвестный")

                            ExpenseHistoryUiModel(
                                title = ExpenseLogParser.parseActionTitle(log),
                                subtitle = "$actorName • ${formatDate(log.timestamp)}",
                                isLast = index == expenseLogs.lastIndex,
                            )
                        }

                    ExpenseDetailsUiState.Success(
                        ExpenseFullDetails(
                            expense,
                            splits,
                            historyUiModels,
                            participants,
                            currency,
                        ),
                    )
                }
            }
        }.stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            ExpenseDetailsUiState.Loading,
        )

    override fun handleIntent(intent: ExpenseDetailsIntent) {
        when (intent) {
            is ExpenseDetailsIntent.DeleteExpense -> deleteExpense()
        }
    }

    private fun deleteExpense() {
        screenModelScope.launch {
            val expense = expenseRepository.getExpenseById(expenseId).firstOrNull()
            if (expense?.remoteId != null) {
                expenseRepository.deleteExpenseOnline(expense.remoteId, tripId)
            }
            expenseRepository.deleteExpense(expenseId)
            _isDeleted.value = true
            sendEffect(ExpenseDetailsEffect.NavigateBack)
        }
    }
}
