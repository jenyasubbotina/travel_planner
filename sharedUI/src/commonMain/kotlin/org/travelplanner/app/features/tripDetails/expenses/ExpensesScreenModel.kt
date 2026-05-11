package org.travelplanner.app.features.tripDetails.expenses

import cafe.adriel.voyager.core.model.screenModelScope
import org.travelplanner.app.AppBackground
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
import org.travelplanner.app.core.toMoneyDouble
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.ExpenseSplit
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.domain.Trip

class ExpensesScreenModel(
    private val tripId: String,
    private val expenseRepository: ExpenseRepository,
    private val participantRepository: ParticipantRepository,
    private val tripRepository: TripRepository,
    private val userSession: UserSession,
    private val globalNotifier: GlobalNotifier,
) : ReactiveScreenModel<ExpensesState, ExpensesIntent, ExpensesEffect>() {
    private val _searchQuery = MutableStateFlow("")
    private val _activeCategory = MutableStateFlow("ALL")

    init {
        screenModelScope.launch(AppBackground) {
            try {
                expenseRepository.syncExpenses(tripId)
            } catch (e: Exception) {
                println("Background sync failed: ${e.message}")
            }
        }
    }

    private val dataBundle =
        combine(
            expenseRepository.getExpensesFlow(tripId),
            expenseRepository.getSplitsFlow(tripId),
            participantRepository.getParticipantsFlow(tripId),
            tripRepository.getTripById(tripId),
            userSession.currentUser,
        ) { expenses, splits, participants, trip, user ->
            DataBundle(expenses, splits, participants, trip, user)
        }

    override val state: StateFlow<ExpensesState> =
        combine(
            dataBundle,
            _searchQuery,
            _activeCategory,
        ) { bundle, query, category ->
            val allEntries = bundle.expenses
            val allSplits = bundle.splits
            val participants = bundle.participants
            val currentUser = bundle.user

            val validExpenses =
                allEntries.filter { it.category != expenseRepository.CATEGORY_PAYMENT }
            val realTotal = validExpenses.sumOf { it.amount.toMoneyDouble() }

            val myLocalId =
                currentUser?.let { user ->
                    participants.find { it.userId == user.id }?.id
                }

            val realUserShare =
                if (myLocalId == null) {
                    0.0
                } else {
                    validExpenses.sumOf { expense ->
                        val expenseSplits = allSplits.filter { it.expenseId == expense.id }
                        if (expenseSplits.isNotEmpty()) {
                            val myUserId = currentUser?.id
                            expenseSplits.find { it.participantId == myUserId }?.amount?.toMoneyDouble() ?: 0.0
                        } else {
                            if (participants.isNotEmpty()) expense.amount.toMoneyDouble() / participants.size else 0.0
                        }
                    }
                }

            val displayedExpenses =
                validExpenses
                    .filter { expense ->
                        val matchesSearch = expense.title.contains(query, ignoreCase = true)
                        val matchesCategory =
                            if (category == "ALL") true else expense.category == category
                        matchesSearch && matchesCategory
                    }.sortedWith(
                        compareByDescending<Expense> { it.pendingUpdateJson != null }
                            .thenByDescending { it.date },
                    )

            val isOwner = bundle.trip?.ownerUserId == currentUser?.id

            ExpensesState(
                expenses = displayedExpenses,
                participants = participants,
                isOwner = isOwner,
                currentUserId = currentUser?.id,
                totalAmount = realTotal,
                userShare = realUserShare,
                searchQuery = query,
                activeCategory = category,
                currency = bundle.trip?.currency ?: "¥",
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), ExpensesState())

    private data class DataBundle(
        val expenses: List<Expense>,
        val splits: List<ExpenseSplit>,
        val participants: List<Participant>,
        val trip: Trip?,
        val user: AppUser?,
    )

    override fun handleIntent(intent: ExpensesIntent) {
        when (intent) {
            is ExpensesIntent.Search -> {
                _searchQuery.value = intent.query
            }

            is ExpensesIntent.CategorySelect -> {
                _activeCategory.value = intent.category
            }

            is ExpensesIntent.ResolveConflict -> {
                screenModelScope.launch {
                    runConflictAction {
                        expenseRepository.resolveConflict(
                            intent.tripId,
                            intent.expenseRemoteId,
                            intent.accept,
                        )
                    }
                }
            }

            is ExpensesIntent.MergeConflict -> {
                screenModelScope.launch {
                    runConflictAction {
                        expenseRepository.mergeConflict(
                            intent.tripId,
                            intent.expenseRemoteId,
                            intent.merged,
                        )
                    }
                }
            }

            is ExpensesIntent.RevertConflict -> {
                screenModelScope.launch {
                    runConflictAction {
                        expenseRepository.revertConflict(
                            intent.tripId,
                            intent.expenseRemoteId,
                        )
                    }
                }
            }
        }
    }

    private suspend inline fun runConflictAction(crossinline block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[expense-conflict] ${e::class.simpleName}: ${e.message}")
            globalNotifier.notifyError("Не удалось разрешить конфликт")
        }
    }
}
