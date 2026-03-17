package org.travelplanner.app.core

import kotlinx.serialization.Serializable

@Serializable
data class CreateExpenseRequest(
    val title: String,
    val amount: Double,
    val category: String,
    val payerUserId: String,
    val date: Long,
    val splits: List<SplitDto>,
    val imageUrl: String? = null,
)

@Serializable
data class TripDto(
    val id: Long,
    val title: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val totalBudget: Double,
    val description: String?,
    val ownerUserId: String,
    val joinCode: String,
    val currency: String = "¥",
    val imageUrl: String? = null,
    val filesJson: String = "[]",
)

@Serializable
data class CreateTripRequest(
    val title: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val totalBudget: Double,
    val description: String?,
    val ownerUserId: String,
    val ownerName: String,
    val ownerEmail: String,
    val currency: String = "¥",
    val imageUrl: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
)

@Serializable
data class JoinTripRequest(
    val userId: String,
    val userName: String,
)

@Serializable
data class JoinByCodeRequest(
    val code: String,
    val userId: String,
    val userName: String,
)

@Serializable
data class UpdateBudgetRequest(
    val totalBudget: Double,
)

@Serializable
data class PendingExpenseUpdateDto(
    val editorUserId: String,
    val editorName: String,
    val timestamp: Long,
    val proposedExpense: CreateExpenseRequest,
)

@Serializable
data class ExpenseDto(
    val id: String,
    val tripId: Long,
    val title: String,
    val amount: Double,
    val category: String,
    val payerUserId: String,
    val date: Long,
    val splits: List<SplitDto>,
    val creatorUserId: String,
    val pendingUpdate: PendingExpenseUpdateDto? = null,
    val imageUrl: String? = null,
)

@Serializable
data class SplitDto(
    val userId: String,
    val amount: Double,
)

@Serializable
data class ChecklistItemDto(
    val id: String,
    val tripId: Long,
    val title: String,
    val isGroup: Boolean,
    val ownerUserId: String,
    val completedBy: List<String>,
)

@Serializable
data class CreateChecklistItemRequest(
    val title: String,
    val isGroup: Boolean,
)
