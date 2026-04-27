package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.travelplanner.app.TripEventEntity
import org.travelplanner.app.core.AddPointCommentRequest
import org.travelplanner.app.core.AddPointLinkRequest
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.CreateAttachmentRequest
import org.travelplanner.app.core.ItineraryPointResponse
import org.travelplanner.app.core.PresignUploadRequest
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.TripUtils.isoToEpochMillis
import org.travelplanner.app.core.V2CreateItineraryPointRequest
import org.travelplanner.app.core.V2UpdateItineraryPointRequest
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Event
import org.travelplanner.app.domain.toDomain
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventCommentDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventFileDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventLinkDto

internal fun mimeTypeForFileName(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else -> "application/octet-stream"
    }

class EventRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val json: Json,
) {
    private val queries = db.eventsQueries

    private fun getEventsEntityFlow(tripId: String): Flow<List<TripEventEntity>> =
        queries.getEventsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getEventEntityFlow(eventId: Long): Flow<TripEventEntity?> =
        queries.getEventById(eventId).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun getEventsFlow(tripId: String): Flow<List<Event>> = getEventsEntityFlow(tripId).map { list -> list.map { it.toDomain(json) } }

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
                    type = null,
                    date = null,
                    startTime = null,
                    endTime = null,
                    sortOrder = 0,
                    version = 0,
                    createdBy = "",
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
                    type = null,
                    date = null,
                    startTime = null,
                    endTime = null,
                    sortOrder = 0,
                    version = 0,
                    createdBy = "",
                )
            }
        }
    }

    fun saveEventFromResponse(
        tripId: String,
        response: ItineraryPointResponse,
    ) {
        db.transaction {
            val existingId = queries.getEventIdByRemoteId(response.id).executeAsOneOrNull()
            val existing = existingId?.let { queries.getEventById(it).executeAsOneOrNull() }
            val resolvedCategory =
                response.category ?: response.type ?: existing?.category ?: "OTHER"
            val participantsJson = json.encodeToString(response.participantIds)
            val linksJson = existing?.linksJson ?: "[]"
            val commentsJson = existing?.commentsJson ?: "[]"
            val filesJson = existing?.filesJson ?: "[]"

            if (existingId != null) {
                queries.updateEventFull(
                    tripId = tripId,
                    dayIndex = response.dayIndex.toLong(),
                    time = response.startTime ?: "",
                    title = response.title,
                    subtitle = response.subtitle ?: existing?.subtitle ?: "",
                    description = response.description,
                    duration = response.duration,
                    cost = response.cost ?: existing?.cost ?: 0.0,
                    actualCost = response.actualCost ?: existing?.actualCost ?: 0.0,
                    status = response.status,
                    category = resolvedCategory,
                    latitude = response.latitude ?: 0.0,
                    longitude = response.longitude ?: 0.0,
                    address = response.address,
                    linksJson = linksJson,
                    commentsJson = commentsJson,
                    filesJson = filesJson,
                    participantIdsJson = participantsJson,
                    type = response.type,
                    date = response.date,
                    startTime = response.startTime,
                    endTime = response.endTime,
                    sortOrder = response.sortOrder.toLong(),
                    version = response.version,
                    createdBy = response.createdBy,
                    id = existingId,
                )
            } else {
                queries.insertOrReplaceEvent(
                    remoteId = response.id,
                    tripId = tripId,
                    dayIndex = response.dayIndex.toLong(),
                    time = response.startTime ?: "",
                    title = response.title,
                    subtitle = response.subtitle ?: "",
                    description = response.description,
                    duration = response.duration,
                    cost = response.cost ?: 0.0,
                    actualCost = response.actualCost ?: 0.0,
                    status = response.status,
                    category = resolvedCategory,
                    latitude = response.latitude ?: 0.0,
                    longitude = response.longitude ?: 0.0,
                    address = response.address,
                    linksJson = linksJson,
                    commentsJson = commentsJson,
                    filesJson = filesJson,
                    participantIdsJson = participantsJson,
                    type = response.type,
                    date = response.date,
                    startTime = response.startTime,
                    endTime = response.endTime,
                    sortOrder = response.sortOrder.toLong(),
                    version = response.version,
                    createdBy = response.createdBy,
                )
            }
        }
    }

    suspend fun syncEvents(tripId: String) {
        try {
            val remoteEvents = api.getItinerary(tripId)
            val remoteIds = remoteEvents.map { it.id }.toSet()

            db.transaction {
                val localEvents = queries.getEventsForTrip(tripId).executeAsList()
                localEvents.forEach { local ->
                    if (local.remoteId != null && local.remoteId !in remoteIds) {
                        queries.deleteEventByRemoteId(local.remoteId)
                    }
                }
                remoteEvents.forEach { response ->
                    saveEventFromResponse(tripId, response)
                }
            }
        } catch (e: Exception) {
            println("Event sync failed (Offline mode): ${e.message}")
        }
    }

    suspend fun addEventOnline(
        tripId: String,
        dto: EventDto,
    ) {
        val request =
            V2CreateItineraryPointRequest(
                title = dto.title,
                description = dto.description,
                subtitle = dto.subtitle.ifBlank { null },
                type = dto.category,
                category = dto.category,
                date = null,
                dayIndex = dto.dayIndex,
                startTime = dto.time.ifBlank { null },
                duration = dto.duration?.ifBlank { null },
                latitude = if (dto.latitude != 0.0) dto.latitude else null,
                longitude = if (dto.longitude != 0.0) dto.longitude else null,
                address = dto.address,
                cost = dto.cost.takeIf { it > 0.0 },
                actualCost = dto.actualCost?.takeIf { it > 0.0 },
                status = dto.status.takeIf { it.isNotBlank() && it != "NONE" },
                participantIds = dto.participantIds.takeIf { it.isNotEmpty() },
            )
        val response = api.createItineraryPoint(tripId, request)
        saveEventFromResponse(tripId, response)
    }

    suspend fun updateEventOnline(
        tripId: String,
        eventId: Long,
        remoteId: String,
        dto: EventDto,
    ) {
        val existingEntity = queries.getEventById(eventId).executeAsOneOrNull()
        val request =
            V2UpdateItineraryPointRequest(
                title = dto.title,
                description = dto.description,
                subtitle = dto.subtitle,
                type = dto.category,
                category = dto.category,
                dayIndex = dto.dayIndex,
                startTime = dto.time.ifBlank { null },
                duration = dto.duration,
                latitude = if (dto.latitude != 0.0) dto.latitude else null,
                longitude = if (dto.longitude != 0.0) dto.longitude else null,
                address = dto.address,
                cost = dto.cost,
                actualCost = dto.actualCost,
                status = dto.status,
                participantIds = dto.participantIds,
                expectedVersion = existingEntity?.version,
            )
        val response = api.updateItineraryPoint(tripId, remoteId, request)
        saveEventFromResponse(tripId, response)
    }

    suspend fun uploadFileAndAdd(
        tripId: String,
        eventId: Long,
        currentDto: EventDto,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
        category: String,
    ) {
        val attachment = api.uploadFile(tripId, fileBytes, fileName, mimeType)
        val newFile = EventFileDto(name = fileName, url = attachment.s3Key, type = category)
        val updatedDto = currentDto.copy(files = currentDto.files + newFile)
        updateEventOnline(tripId, eventId, currentDto.id, updatedDto)
    }

    fun deleteEventLocal(id: Long) = queries.deleteEvent(id)

    fun deleteEventByRemoteId(remoteId: String) = queries.deleteEventByRemoteId(remoteId)

    suspend fun deleteEventOnline(
        tripId: String,
        eventId: Long,
        remoteId: String?,
    ) {
        if (!remoteId.isNullOrBlank()) {
            api.deleteItineraryPoint(tripId, remoteId)
        }
        deleteEventLocal(eventId)
    }

    fun reconcileFilesJsonFromAttachments(remoteId: String) {
        db.transaction {
            val eventId =
                queries.getEventIdByRemoteId(remoteId).executeAsOneOrNull()
                    ?: return@transaction
            val entity =
                queries.getEventById(eventId).executeAsOneOrNull()
                    ?: return@transaction
            val files =
                db.attachmentsQueries
                    .getAttachmentsForPoint(remoteId)
                    .executeAsList()
                    .sortedBy { it.createdAt }
                    .map { att ->
                        EventFileDto(
                            name = att.fileName,
                            url = att.s3Key,
                            type = if (att.mimeType.startsWith("image/")) "PHOTO" else "DOCUMENT",
                        )
                    }
            queries.updateEvent(
                time = entity.time,
                title = entity.title,
                subtitle = entity.subtitle,
                description = entity.description,
                cost = entity.cost,
                actualCost = entity.actualCost,
                category = entity.category,
                address = entity.address,
                linksJson = entity.linksJson,
                commentsJson = entity.commentsJson,
                filesJson = json.encodeToString(files),
                participantIdsJson = entity.participantIdsJson,
                id = eventId,
            )
        }
    }

    suspend fun refreshPointFiles(
        tripId: String,
        remoteId: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        try {
            val files =
                api
                    .listTripFiles(tripId, scope = "all")
                    .filter { it.pointId == remoteId }
                    .sortedBy { it.createdAt }
                    .map {
                        EventFileDto(
                            name = it.fileName,
                            url = it.s3Key,
                            type = if (it.mimeType.startsWith("image/")) "PHOTO" else "DOCUMENT",
                        )
                    }

            db.transaction {
                val existingId =
                    queries.getEventIdByRemoteId(remoteId).executeAsOneOrNull()
                        ?: return@transaction
                val entity =
                    queries.getEventById(existingId).executeAsOneOrNull()
                        ?: return@transaction
                queries.updateEvent(
                    time = entity.time,
                    title = entity.title,
                    subtitle = entity.subtitle,
                    description = entity.description,
                    cost = entity.cost,
                    actualCost = entity.actualCost,
                    category = entity.category,
                    address = entity.address,
                    linksJson = entity.linksJson,
                    commentsJson = entity.commentsJson,
                    filesJson = json.encodeToString(files),
                    participantIdsJson = entity.participantIdsJson,
                    id = existingId,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun addPointLink(
        tripId: String,
        remoteId: String,
        title: String,
        url: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        val response = api.addPointLink(tripId, remoteId, AddPointLinkRequest(title, url)) ?: return
        appendLinkLocally(remoteId, EventLinkDto(title = response.title, url = response.url))
    }

    suspend fun deletePointLink(
        tripId: String,
        remoteId: String,
        linkId: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        api.deletePointLink(tripId, remoteId, linkId)
    }

    suspend fun addPointComment(
        tripId: String,
        remoteId: String,
        text: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        val response = api.addPointComment(tripId, remoteId, AddPointCommentRequest(text)) ?: return
        appendCommentLocally(
            remoteId = remoteId,
            comment =
                EventCommentDto(
                    userId = response.authorUserId,
                    userName = response.authorDisplayName,
                    text = response.text,
                    timestamp = runCatching { isoToEpochMillis(response.createdAt) }.getOrDefault(0L),
                ),
        )
    }

    suspend fun uploadPointFile(
        tripId: String,
        remoteId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        val presigned =
            api.presignUpload(
                PresignUploadRequest(fileName, mimeType, fileBytes.size.toLong(), tripId),
            )
        val s3Client = HttpClient()
        try {
            s3Client.put(presigned.uploadUrl) {
                header("Content-Type", mimeType)
                setBody(fileBytes)
            }
        } finally {
            s3Client.close()
        }
        api.createPointAttachment(
            tripId = tripId,
            pointId = remoteId,
            request =
                CreateAttachmentRequest(
                    fileName = fileName,
                    fileSize = fileBytes.size.toLong(),
                    mimeType = mimeType,
                    s3Key = presigned.s3Key,
                ),
        )
        refreshPointFiles(tripId, remoteId)
    }

    private fun appendLinkLocally(
        remoteId: String,
        link: EventLinkDto,
    ) {
        db.transaction {
            val existingId =
                queries.getEventIdByRemoteId(remoteId).executeAsOneOrNull()
                    ?: return@transaction
            val entity =
                queries.getEventById(existingId).executeAsOneOrNull()
                    ?: return@transaction
            val current: List<EventLinkDto> =
                runCatching {
                    json.decodeFromString<List<EventLinkDto>>(entity.linksJson ?: "[]")
                }.getOrDefault(emptyList())
            val updated = current + link
            queries.updateEvent(
                time = entity.time,
                title = entity.title,
                subtitle = entity.subtitle,
                description = entity.description,
                cost = entity.cost,
                actualCost = entity.actualCost,
                category = entity.category,
                address = entity.address,
                linksJson = json.encodeToString(updated),
                commentsJson = entity.commentsJson,
                filesJson = entity.filesJson,
                participantIdsJson = entity.participantIdsJson,
                id = existingId,
            )
        }
    }

    private fun appendCommentLocally(
        remoteId: String,
        comment: EventCommentDto,
    ) {
        db.transaction {
            val existingId =
                queries.getEventIdByRemoteId(remoteId).executeAsOneOrNull()
                    ?: return@transaction
            val entity =
                queries.getEventById(existingId).executeAsOneOrNull()
                    ?: return@transaction
            val current: List<EventCommentDto> =
                runCatching {
                    json.decodeFromString<List<EventCommentDto>>(entity.commentsJson ?: "[]")
                }.getOrDefault(emptyList())
            val updated = current + comment
            queries.updateEvent(
                time = entity.time,
                title = entity.title,
                subtitle = entity.subtitle,
                description = entity.description,
                cost = entity.cost,
                actualCost = entity.actualCost,
                category = entity.category,
                address = entity.address,
                linksJson = entity.linksJson,
                commentsJson = json.encodeToString(updated),
                filesJson = entity.filesJson,
                participantIdsJson = entity.participantIdsJson,
                id = existingId,
            )
        }
    }
}
