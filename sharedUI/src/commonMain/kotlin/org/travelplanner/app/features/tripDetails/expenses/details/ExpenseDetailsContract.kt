package org.travelplanner.app.features.tripDetails.expenses.details

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.ExpenseSplit
import org.travelplanner.app.domain.Participant

data class ExpenseHistoryUiModel(
    val title: String,
    val subtitle: String,
    val isLast: Boolean,
)

data class ExpenseFullDetails(
    val expense: Expense,
    val splits: List<ExpenseSplit>,
    val history: List<ExpenseHistoryUiModel>,
    val participants: List<Participant>,
    val currency: String = "¥",
    val canEdit: Boolean = true,
)

sealed class ExpenseDetailsUiState : UiState {
    data object Loading : ExpenseDetailsUiState()

    data class Success(
        val details: ExpenseFullDetails,
    ) : ExpenseDetailsUiState()

    data object Deleted : ExpenseDetailsUiState()

    data object Error : ExpenseDetailsUiState()
}

sealed interface ExpenseDetailsIntent : UiIntent {
    data object DeleteExpense : ExpenseDetailsIntent
}

sealed interface ExpenseDetailsEffect : UiEffect {
    data object NavigateBack : ExpenseDetailsEffect
}
