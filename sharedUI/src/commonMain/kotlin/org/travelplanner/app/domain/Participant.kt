package org.travelplanner.app.domain

data class Participant(
    val id: Long,
    val tripId: Long,
    val userId: String?,
    val name: String,
    val email: String,
    val role: String,
    val avatarColor1: String,
    val avatarColor2: String,
)
