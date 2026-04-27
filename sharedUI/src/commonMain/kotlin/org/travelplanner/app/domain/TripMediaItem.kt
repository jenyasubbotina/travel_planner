package org.travelplanner.app.domain

enum class MediaType { IMAGE, DOCUMENT }

data class TripMediaItem(
    val url: String,
    val title: String,
    val subtitle: String,
    val type: MediaType,
    val date: Long,
    val category: String,
    val s3Key: String? = null,
)
