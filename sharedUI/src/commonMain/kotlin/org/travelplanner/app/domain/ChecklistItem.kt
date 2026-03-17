package org.travelplanner.app.domain

data class ChecklistItem(
    val id: String,
    val tripId: Long,
    val title: String,
    val isGroup: Boolean,
    val ownerUserId: String,
    val completedBy: List<String>,
)
