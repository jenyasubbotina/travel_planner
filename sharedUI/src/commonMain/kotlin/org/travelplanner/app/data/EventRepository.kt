package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.travelplanner.app.TripEventEntity
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Event
import org.travelplanner.app.domain.toDomain
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventFileDto

class EventRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val json: Json,
) {
    private val queries = db.eventsQueries

    fun resolveUrl(path: String?): String? = api.resolveUrl(path)

    private fun getEventsEntityFlow(tripId: Long): Flow<List<TripEventEntity>> =
        queries.getEventsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getEventEntityFlow(eventId: Long): Flow<TripEventEntity?> =
        queries.getEventById(eventId).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun getEventsFlow(tripId: Long): Flow<List<Event>> = getEventsEntityFlow(tripId).map { list -> list.map { it.toDomain(json) } }

    fun getEventFlow(eventId: Long): Flow<Event?> = getEventEntityFlow(eventId).map { it?.toDomain(json) }

    fun saveEventLocally(dto: EventDto) {
        db.transaction {
            val existingId = queries.getEventIdByRemoteId(dto.id).executeAsOneOrNull()

            if (existingId != null) {
                queries.updateEventFull(
                    tripId = dto.tripId,
                    dayIndex = dto.dayIndex.toLong(),
                    time = dto.time,
                    title = dto.title,
                    subtitle = dto.subtitle,
                    description = dto.description,
                    duration = dto.duration,
                    cost = dto.cost,
                    actualCost = dto.actualCost ?: 0.0,
                    status = dto.status,
                    category = dto.category,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    address = dto.address,
                    linksJson = json.encodeToString(dto.links),
                    commentsJson = json.encodeToString(dto.comments),
                    filesJson = json.encodeToString(dto.files),
                    participantIdsJson = json.encodeToString(dto.participantIds),
                    id = existingId,
                )
            } else {
                queries.insertOrReplaceEvent(
                    remoteId = dto.id,
                    tripId = dto.tripId,
                    dayIndex = dto.dayIndex.toLong(),
                    time = dto.time,
                    title = dto.title,
                    subtitle = dto.subtitle,
                    description = dto.description,
                    duration = dto.duration,
                    cost = dto.cost,
                    actualCost = dto.actualCost ?: 0.0,
                    status = dto.status,
                    category = dto.category,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    address = dto.address,
                    linksJson = json.encodeToString(dto.links),
                    commentsJson = json.encodeToString(dto.comments),
                    filesJson = json.encodeToString(dto.files),
                    participantIdsJson = json.encodeToString(dto.participantIds),
                )
            }
        }
    }

    suspend fun syncEvents(tripId: Long) {
        try {
            val remoteEvents = api.getEvents(tripId)
            val remoteIds = remoteEvents.map { it.id }.toSet()

            db.transaction {
                val localEvents = queries.getEventsForTrip(tripId).executeAsList()
                localEvents.forEach { local ->
                    if (local.remoteId != null && local.remoteId !in remoteIds) {
                        queries.deleteEventByRemoteId(local.remoteId)
                    }
                }
                remoteEvents.forEach { dto ->
                    saveEventLocally(dto)
                }
            }
        } catch (e: Exception) {
            println("Event sync failed (Offline mode): ${e.message}")
        }
    }

    suspend fun addEventOnline(
        tripId: Long,
        dto: EventDto,
    ) {
        val saved = api.addEvent(tripId, dto)

        saveEventLocally(saved)
    }

    suspend fun updateEventOnline(
        tripId: Long,
        eventId: Long,
        remoteId: String,
        dto: EventDto,
    ) {
        val updated = api.updateEvent(tripId, dto)
        saveEventLocally(updated)
    }

    suspend fun uploadFileAndAdd(
        tripId: Long,
        eventId: Long,
        currentDto: EventDto,
        fileBytes: ByteArray,
        fileName: String,
        type: String,
    ) {
        val uploadedUrl = api.uploadFile(fileBytes, fileName)

        val newFile = EventFileDto(name = fileName, url = uploadedUrl, type = type)
        val updatedDto = currentDto.copy(files = currentDto.files + newFile)

        updateEventOnline(tripId, eventId, currentDto.id, updatedDto)
    }

    fun deleteEventLocal(id: Long) = queries.deleteEvent(id)

    fun deleteEventByRemoteId(remoteId: String) = queries.deleteEventByRemoteId(remoteId)

    suspend fun deleteEventOnline(
        tripId: Long,
        eventId: Long,
        remoteId: String?,
    ) {
        if (!remoteId.isNullOrBlank()) {
            api.deleteEvent(tripId, remoteId)
        }
        deleteEventLocal(eventId)
    }
}
