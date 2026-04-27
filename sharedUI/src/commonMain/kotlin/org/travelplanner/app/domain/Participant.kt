package org.travelplanner.app.domain

data class Participant(
    val id: Long,
    val tripId: String,
    val userId: String,
    val name: String,
    val email: String,
    val role: String,
    val avatarUrl: String? = null,
    val joinedAt: String? = null,
)
