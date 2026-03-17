package org.travelplanner.app.features.tripDetails.expenses

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.Participant

data class ExpensesState(
    val expenses: List<Expense> = emptyList(),
    val participants: List<Participant> = emptyList(),
    val isOwner: Boolean = false,
    val totalAmount: Double = 0.0,
    val userShare: Double = 0.0,
    val searchQuery: String = "",
    val activeCategory: String = "ALL",
    val isAddSheetVisible: Boolean = false,
    val currency: String = "¥",
) : UiState

sealed interface ExpensesIntent : UiIntent {
    data class Search(
        val query: String,
    ) : ExpensesIntent

    data class CategorySelect(
        val category: String,
    ) : ExpensesIntent

    data class ResolveConflict(
        val tripId: Long,
        val expenseRemoteId: String,
        val accept: Boolean,
    ) : ExpensesIntent
}

sealed interface ExpensesEffect : UiEffect
