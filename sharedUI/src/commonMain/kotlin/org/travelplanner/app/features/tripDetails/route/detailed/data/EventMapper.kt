package org.travelplanner.app.features.tripDetails.route.detailed.data

import kotlinx.serialization.json.Json
import org.travelplanner.app.TripEventEntity
import org.travelplanner.app.domain.Event

fun TripEventEntity.getParsedComments(json: Json): List<EventCommentDto> {
    if (this.commentsJson.isNullOrBlank() || this.commentsJson == "null") return emptyList()
    return try {
        json.decodeFromString(this.commentsJson)
    } catch (e: Exception) {
        emptyList()
    }
}

fun TripEventEntity.getParsedLinks(json: Json): List<EventLinkDto> {
    if (this.linksJson.isNullOrBlank() || this.linksJson == "null") return emptyList()
    return try {
        json.decodeFromString(this.linksJson)
    } catch (e: Exception) {
        emptyList()
    }
}

fun TripEventEntity.getParsedFiles(json: Json): List<EventFileDto> {
    if (this.filesJson.isNullOrBlank() || this.filesJson == "null") return emptyList()
    return try {
        json.decodeFromString(this.filesJson)
    } catch (e: Exception) {
        emptyList()
    }
}

fun TripEventEntity.toDto(json: Json): EventDto =
    EventDto(
        id = this.remoteId ?: "",
        tripId = this.tripId,
        dayIndex = this.dayIndex.toInt(),
        time = this.time,
        title = this.title,
        subtitle = this.subtitle,
        description = this.description,
        duration = this.duration,
        cost = this.cost ?: 0.0,
        actualCost = this.actualCost ?: 0.0,
        status = this.status ?: "NONE",
        category = this.category ?: "OTHER",
        latitude = this.latitude ?: 0.0,
        longitude = this.longitude ?: 0.0,
        address = this.address,
        links = this.getParsedLinks(json),
        comments = this.getParsedComments(json),
        files = this.getParsedFiles(json),
        participantIds = this.getParsedParticipantIds(json),
    )

fun TripEventEntity.getParsedParticipantIds(json: Json): List<String> {
    if (this.participantIdsJson.isNullOrBlank() || this.participantIdsJson == "null") return emptyList()
    return try {
        json.decodeFromString(this.participantIdsJson)
    } catch (e: Exception) {
        emptyList()
    }
}

fun Event.toDto(): EventDto =
    EventDto(
        id = this.remoteId ?: "",
        tripId = this.tripId,
        dayIndex = this.dayIndex,
        time = this.time,
        title = this.title,
        subtitle = this.subtitle,
        description = this.description,
        duration = this.duration,
        cost = this.cost,
        actualCost = this.actualCost,
        status = this.status,
        category = this.category,
        latitude = this.latitude,
        longitude = this.longitude,
        address = this.address,
        links = this.links.map { EventLinkDto(it.title, it.url) },
        comments =
            this.comments.map {
                EventCommentDto(
                    it.userId,
                    it.userName,
                    it.text,
                    it.timestamp,
                )
            },
        files = this.files.map { EventFileDto(it.name, it.url, it.type) },
        participantIds = this.participantIds,
    )
