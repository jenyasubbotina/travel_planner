package org.travelplanner.app.domain

data class Trip(
    val id: String = "",
    val title: String,
    val destination: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val currency: String,
    val totalBudget: String = "0",
    val spentAmount: String = "0",
    val description: String?,
    val participantCount: Int = 1,
    val status: String = "PLANNED",
    val joinCode: String? = null,
    val ownerUserId: String,
    val imageUrl: String? = null,
    val filesJson: String? = null,
    val baseCurrency: String = "USD",
    val version: Long = 0,
    val createdBy: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
