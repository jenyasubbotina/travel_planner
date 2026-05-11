package org.travelplanner.app.domain

data class Expense(
    val id: String,
    val tripId: String,
    val title: String,
    val amount: String,
    val category: String,
    val payerName: String,
    val date: String,
    val splitDescription: String,
    val currency: String,
    val creatorUserId: String,
    val pendingUpdateJson: String?,
    val imageUrl: String?,
    val version: Long = 0,
    val splitType: String = "EQUAL",
)

data class ExpenseSplit(
    val id: Long,
    val expenseId: String,
    val participantId: String,
    val amount: String,
    val value: String = "0",
    val isPaid: Boolean,
)
