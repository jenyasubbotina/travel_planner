package org.travelplanner.app.features.tripDetails.balance

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState

data class BalanceUiState(
    val currentUserNetBalance: Double = 0.0,
    val involvementCount: Int = 0,
    val participants: List<ParticipantBalanceItem> = emptyList(),
    val paymentsToMake: List<SuggestedPayment> = emptyList(),
    val history: List<PaymentHistoryItem> = emptyList(),
    val currency: String = "¥",
) : UiState

data class ParticipantBalanceItem(
    val id: Long,
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    val spent: Double,
    val netBalance: Double,
    val isCurrentUser: Boolean,
)

data class SuggestedPayment(
    val fromId: Long,
    val fromName: String,
    val toId: Long,
    val toName: String,
    val amount: Double,
)

data class PaymentHistoryItem(
    val id: Long,
    val title: String,
    val date: Long,
    val amount: Double,
)

sealed interface BalanceIntent : UiIntent {
    data class MarkAsPaid(
        val payment: SuggestedPayment,
    ) : BalanceIntent
}

sealed interface BalanceEffect : UiEffect
