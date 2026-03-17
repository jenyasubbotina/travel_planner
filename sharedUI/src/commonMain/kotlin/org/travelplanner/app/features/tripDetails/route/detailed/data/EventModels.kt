package org.travelplanner.app.features.tripDetails.route.detailed.data

import kotlinx.serialization.Serializable

@Serializable
data class EventLinkDto(
    val title: String,
    val url: String,
)

@Serializable
data class EventCommentDto(
    val userId: String,
    val userName: String,
    val text: String,
    val timestamp: Long,
)

@Serializable
data class EventFileDto(
    val name: String,
    val url: String,
    val type: String = "DOCUMENT",
)

@Serializable
data class EventDto(
    val id: String,
    val tripId: Long,
    val dayIndex: Int,
    val time: String,
    val title: String,
    val subtitle: String,
    val description: String?,
    val duration: String?,
    val cost: Double,
    val actualCost: Double? = 0.0,
    val status: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val links: List<EventLinkDto> = emptyList(),
    val comments: List<EventCommentDto> = emptyList(),
    val files: List<EventFileDto> = emptyList(),
    val participantIds: List<String> = emptyList(),
)
