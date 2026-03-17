package org.travelplanner.app.domain

data class Event(
    val id: Long,
    val remoteId: String?,
    val tripId: Long,
    val dayIndex: Int,
    val time: String,
    val title: String,
    val subtitle: String,
    val description: String?,
    val duration: String?,
    val cost: Double,
    val actualCost: Double,
    val status: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val links: List<EventLink>,
    val comments: List<EventComment>,
    val files: List<EventFile>,
    val participantIds: List<String>,
)

data class EventLink(
    val title: String,
    val url: String,
)

data class EventComment(
    val userId: String,
    val userName: String,
    val text: String,
    val timestamp: Long,
)

data class EventFile(
    val name: String,
    val url: String,
    val type: String,
)
