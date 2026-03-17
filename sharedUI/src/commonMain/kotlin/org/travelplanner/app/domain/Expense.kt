package org.travelplanner.app.domain

data class Expense(
    val id: Long,
    val remoteId: String?,
    val tripId: Long,
    val title: String,
    val amount: Double,
    val category: String,
    val payerName: String,
    val date: Long,
    val splitDescription: String,
    val currency: String,
    val creatorUserId: String,
    val pendingUpdateJson: String?,
    val imageUrl: String?,
)

data class ExpenseSplit(
    val id: Long,
    val expenseId: Long,
    val participantId: Long,
    val amount: Double,
    val isPaid: Boolean,
)
