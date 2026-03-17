package org.travelplanner.app.domain

data class Trip(
    val id: Long = 0,
    val title: String,
    val destination: String,
    val startDate: Long,
    val endDate: Long,
    val currency: String,
    val totalBudget: Double,
    val spentAmount: Double = 0.0,
    val description: String?,
    val participantCount: Int = 1,
    val status: String = "PLANNED",
    val joinCode: String? = null,
    val ownerUserId: String,
    val imageUrl: String? = null,
    val filesJson: String? = null,
)
