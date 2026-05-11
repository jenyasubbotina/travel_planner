package org.travelplanner.app.features.tripDetails.expenses

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState
import org.travelplanner.app.domain.Participant
import kotlin.time.Clock

data class AddExpenseState(
    val amount: String = "",
    val category: String = "HOUSING",
    val description: String = "",
    val date: Long = Clock.System.now().toEpochMilliseconds(),
    val payerId: Long? = null,
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val participants: List<ParticipantSplitState> = emptyList(),
    val imageUrl: String? = null,
    val photoBytes: ByteArray? = null,
    val isSplitValid: Boolean = true,
    val splitError: String? = null,
    val currency: String = "¥",
    val showErrors: Boolean = false,
    val amountError: String? = null,
    val descriptionError: String? = null,
    val payerError: String? = null,
    val participantsError: String? = null,
) : UiState

data class ParticipantSplitState(
    val participant: Participant,
    val isSelected: Boolean = true,
    val manualAmount: String = "",
    val calculatedAmount: Double = 0.0,
)

enum class SplitMethod { EQUAL, MANUAL }

sealed interface ExpenseFormIntent : UiIntent {
    data class Initialize(
        val expenseId: String? = null,
    ) : ExpenseFormIntent

    data class AmountChanged(
        val value: String,
    ) : ExpenseFormIntent

    data class CategoryChanged(
        val category: String,
    ) : ExpenseFormIntent

    data class DescriptionChanged(
        val description: String,
    ) : ExpenseFormIntent

    data class PayerChanged(
        val id: Long,
    ) : ExpenseFormIntent

    data class DateChanged(
        val date: Long?,
    ) : ExpenseFormIntent

    data class SplitMethodChanged(
        val method: SplitMethod,
    ) : ExpenseFormIntent

    data class ManualAmountChanged(
        val participantId: Long,
        val value: String,
    ) : ExpenseFormIntent

    data class ParticipantToggled(
        val id: Long,
    ) : ExpenseFormIntent

    data class PhotoSelected(
        val bytes: ByteArray?,
    ) : ExpenseFormIntent

    data object Save : ExpenseFormIntent
}

sealed interface ExpenseFormEffect : UiEffect {
    data object SaveSuccess : ExpenseFormEffect

    data object SaveQueuedForApproval : ExpenseFormEffect

    data object SaveBlockedAnotherPending : ExpenseFormEffect

    data class ShowError(
        val message: String,
    ) : ExpenseFormEffect
}
