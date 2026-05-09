package org.travelplanner.app.features.tripDetails.balance

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.travelplanner.app.core.AppUser
import org.travelplanner.app.core.GlobalNotifier
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.toEpochMillis
import org.travelplanner.app.core.toMoneyDouble
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.ExpenseSplit
import org.travelplanner.app.domain.Participant
import kotlin.math.abs
import kotlin.math.round

class BalanceScreenModel(
    private val tripId: String,
    private val expenseRepository: ExpenseRepository,
    private val participantRepository: ParticipantRepository,
    private val userSession: UserSession,
    private val tripRepository: TripRepository,
    private val globalNotifier: GlobalNotifier,
) : ReactiveScreenModel<BalanceUiState, BalanceIntent, BalanceEffect>() {
    private data class OptimizationUiState(
        val isLoading: Boolean = false,
        val isApplied: Boolean = false,
        val message: String? = null,
        val overriddenPayments: List<SuggestedPayment>? = null,
    )

    private val optimizationState = MutableStateFlow(OptimizationUiState())

    override val state: StateFlow<BalanceUiState> =
        combine(
            expenseRepository.getExpensesFlow(tripId),
            participantRepository.getParticipantsFlow(tripId),
            expenseRepository.getSplitsFlow(tripId),
            userSession.currentUser,
            tripRepository.getTripById(tripId),
            optimizationState,
        ) { flows ->
            val expenses = flows[0] as List<Expense>
            val participants = flows[1] as List<Participant>
            val allSplits = flows[2] as List<ExpenseSplit>
            val currentUser = flows[3] as AppUser?
            val trip = flows[4] as org.travelplanner.app.domain.Trip?
            val optimizationUi = flows[5] as OptimizationUiState
            val baseState =
                calculateDetailedBalance(
                    expenses,
                    participants,
                    allSplits,
                    currentUser,
                    trip?.currency ?: "¥",
                )
            baseState.copy(
                paymentsToMake = optimizationUi.overriddenPayments ?: baseState.paymentsToMake,
                involvementCount = (optimizationUi.overriddenPayments ?: baseState.paymentsToMake).size,
                isOptimizationLoading = optimizationUi.isLoading,
                isOptimizationApplied = optimizationUi.isApplied,
                optimizationMessage = optimizationUi.message,
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), BalanceUiState())

    override fun handleIntent(intent: BalanceIntent) {
        when (intent) {
            is BalanceIntent.MarkAsPaid -> markAsPaid(intent.payment)
            BalanceIntent.OptimizeSettlements -> optimizeSettlements()
        }
    }

    private fun markAsPaid(payment: SuggestedPayment) {
        screenModelScope.launch {
            expenseRepository.settleDebt(
                tripId = tripId,
                debtorId = payment.fromId,
                creditorId = payment.toId,
                amount = payment.amount,
            )
        }
    }

    private fun optimizeSettlements() {
        screenModelScope.launch {
            val currentUi = state.value
            optimizationState.value =
                optimizationState.value.copy(
                    isLoading = true,
                    message = null,
                )

            runCatching {
                val participantsByUserId = currentUi.participants.associateBy { it.userId }
                val currentUserParticipantId = currentUi.participants.firstOrNull { it.isCurrentUser }?.id
                val settlements = expenseRepository.getSettlements(tripId)

                settlements.mapNotNull { settlement ->
                    val fromParticipant = participantsByUserId[settlement.fromUserId] ?: return@mapNotNull null
                    val toParticipant = participantsByUserId[settlement.toUserId] ?: return@mapNotNull null
                    val amount = settlement.amount.toDoubleOrNull() ?: return@mapNotNull null

                    SuggestedPayment(
                        fromId = fromParticipant.id,
                        fromName = fromParticipant.name,
                        toId = toParticipant.id,
                        toName = toParticipant.name,
                        amount = round(amount * 100.0) / 100.0,
                    )
                }.filter { payment ->
                    currentUserParticipantId != null &&
                        (payment.fromId == currentUserParticipantId || payment.toId == currentUserParticipantId)
                }
            }.onSuccess { optimizedPayments ->
                val before = currentUi.paymentsToMake.size
                val after = optimizedPayments.size
                optimizationState.value =
                    optimizationState.value.copy(
                        isLoading = false,
                        isApplied = true,
                        overriddenPayments = optimizedPayments,
                        message = "$before перевода -> $after перевода",
                    )
            }.onFailure {
                optimizationState.value =
                    optimizationState.value.copy(
                        isLoading = false,
                        isApplied = false,
                        message = null,
                    )
                globalNotifier.notifyError("Не удалось оптимизировать расчеты. Попробуйте снова.")
            }
        }
    }

    private fun calculateDetailedBalance(
        allExpenses: List<Expense>,
        participants: List<Participant>,
        allSplits: List<ExpenseSplit>,
        currentUser: AppUser?,
        currency: String,
    ): BalanceUiState {
        val realExpenses = allExpenses.filter { it.category != "PAYMENT" }
        val paymentLogs = allExpenses.filter { it.category == "PAYMENT" }

        val participantSpend = mutableMapOf<Long, Double>()
        val netBalanceMap = mutableMapOf<Long, Double>()

        participants.forEach {
            participantSpend[it.id] = 0.0
            netBalanceMap[it.id] = 0.0
        }

        fun processTransaction(
            expense: Expense,
            isRealSpend: Boolean,
        ) {
            val payer =
                participants.find { it.name == expense.payerName }
                    ?: participants.find { it.userId == expense.payerName }
                    ?: return

            if (isRealSpend) {
                participantSpend[payer.id] = (participantSpend[payer.id] ?: 0.0) + expense.amount.toMoneyDouble()
            }

            val expenseSplits = allSplits.filter { it.expenseId == expense.id }

            if (expenseSplits.isNotEmpty()) {
                expenseSplits.forEach { split ->
                    val splitParticipant = participants.find { it.userId == split.participantId }
                    val splitParticipantId = splitParticipant?.id ?: return@forEach
                    if (splitParticipantId != payer.id) {
                        netBalanceMap[payer.id] = (netBalanceMap[payer.id] ?: 0.0) + split.amount.toMoneyDouble()
                        netBalanceMap[splitParticipantId] =
                            (netBalanceMap[splitParticipantId] ?: 0.0) - split.amount.toMoneyDouble()
                    }
                }
            } else if (isRealSpend) {
                val share = expense.amount.toMoneyDouble() / participants.size
                participants.forEach { p ->
                    if (p.id != payer.id) {
                        netBalanceMap[payer.id] = (netBalanceMap[payer.id] ?: 0.0) + share
                        netBalanceMap[p.id] = (netBalanceMap[p.id] ?: 0.0) - share
                    }
                }
            }
        }

        realExpenses.forEach { processTransaction(it, isRealSpend = true) }
        paymentLogs.forEach { processTransaction(it, isRealSpend = false) }

        val debtors = mutableListOf<Pair<Long, Double>>()
        val creditors = mutableListOf<Pair<Long, Double>>()

        netBalanceMap.forEach { (id, balance) ->
            val rounded = round(balance * 100.0) / 100.0
            if (rounded < -0.01) debtors.add(id to rounded)
            if (rounded > 0.01) creditors.add(id to rounded)
        }

        debtors.sortBy { it.second }
        creditors.sortByDescending { it.second }

        val suggestedPayments = mutableListOf<SuggestedPayment>()
        var i = 0
        var j = 0

        while (i < debtors.size && j < creditors.size) {
            val (debtorId, debtorBalance) = debtors[i]
            val (creditorId, creditorBalance) = creditors[j]

            val amount = minOf(-debtorBalance, creditorBalance)

            suggestedPayments.add(
                SuggestedPayment(
                    fromId = debtorId,
                    fromName = participants.find { it.id == debtorId }?.name ?: "Неизвестный",
                    toId = creditorId,
                    toName = participants.find { it.id == creditorId }?.name ?: "Неизвестный",
                    amount = (round(amount * 100.0) / 100.0),
                ),
            )

            val newDebtorBalance = debtorBalance + amount
            val newCreditorBalance = creditorBalance - amount

            debtors[i] = debtorId to newDebtorBalance
            creditors[j] = creditorId to newCreditorBalance

            if (abs(newDebtorBalance) < 0.01) i++
            if (abs(newCreditorBalance) < 0.01) j++
        }

        val myId =
            currentUser?.let { user -> participants.find { it.userId == user.id }?.id }

        val myRelevantPayments =
            if (myId != null) {
                suggestedPayments.filter { it.fromId == myId || it.toId == myId }
            } else {
                emptyList()
            }

        val participantItems =
            participants.map { p ->
                ParticipantBalanceItem(
                    id = p.id,
                    userId = p.userId,
                    name = p.name,
                    avatarUrl = p.avatarUrl,
                    spent = participantSpend[p.id] ?: 0.0,
                    netBalance = netBalanceMap[p.id] ?: 0.0,
                    isCurrentUser = p.id == myId,
                )
            }

        val historyItems =
            paymentLogs.sortedByDescending { it.date }.map {
                PaymentHistoryItem(
                    id = it.id,
                    title = it.title,
                    date = it.date.toEpochMillis(),
                    amount = it.amount.toMoneyDouble(),
                )
            }

        return BalanceUiState(
            currentUserNetBalance = if (myId != null) netBalanceMap[myId] ?: 0.0 else 0.0,
            involvementCount = myRelevantPayments.size,
            participants = participantItems,
            paymentsToMake = myRelevantPayments,
            history = historyItems,
            currency = currency,
        )
    }
}
